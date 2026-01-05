package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import utils.ServerResponse; // Importante per il broadcast JSON

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMain {
    // --- CONFIGURAZIONE ---
    public static final String configFile = "server.properties";
    public static int port;
    public static String dataFilePath;
    public static String usersFilePath;
    public static int gameDuration;
    public static int maxErrors;

    // Stato del server
    private Game currentGame;
    private Selector selector;
    private volatile boolean running = true;
    private long gameStartTime;
    
    // Lista thread-safe dei client connessi per il Broadcast
    private Set<SocketChannel> connectedClients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try {
            readConfig();
        } catch (IOException e) {
            System.err.println("Errore config: " + e.getMessage());
            System.exit(1);
        }
        UserManager.getInstance(); 
        new ServerMain().start();
    }

    public static void readConfig() throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);
            port = Integer.parseInt(prop.getProperty("port", "8080"));
            dataFilePath = prop.getProperty("dataFilePath", "data/Connections_Data.json");
            usersFilePath = prop.getProperty("usersFilePath", "data/Users.json");
            gameDuration = Integer.parseInt(prop.getProperty("gameDuration", "60"));
            maxErrors = Integer.parseInt(prop.getProperty("maxErrors", "4"));
        }
    }

    public void start() {
        Thread gameThread = new Thread(this::gameLoop);
        gameThread.start();
        startConsoleListener();

        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port)); 
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            System.out.println("Server avviato sulla porta " + port);

            while (running) {
                selector.select(); 
                if (!running) break;
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (!key.isValid()) continue;
                    try {
                        if (key.isAcceptable()) acceptConnection(serverChannel);
                        else if (key.isReadable()) ClientHandler.handleRead(key, this);
                    } catch (IOException e) {
                        disconnectClient(key); // Gestione disconnessione pulita
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeServer();
        }
    }

    // --- GAME LOOP AGGIORNATO CON BROADCAST ---
    private void gameLoop() {
        try (JsonReader reader = new JsonReader(new FileReader(dataFilePath))) {
            Gson gson = new Gson();
            reader.beginArray();
            while (reader.hasNext() && running) { 
                Game g = gson.fromJson(reader, Game.class);
                setCurrentGame(g);
                this.gameStartTime = System.currentTimeMillis(); 
                
                // Reset sessioni per la nuova partita (pulisce errori vecchi)
                for (SelectionKey key : selector.keys()) {
                    if (key.attachment() instanceof ClientSession) {
                        ((ClientSession) key.attachment()).resetGameStatus();
                    }
                }

                System.out.println("--- NUOVA PARTITA ID: " + g.getGameId() + " ---");
                broadcastGameUpdate("NUOVA PARTITA INIZIATA! ID: " + g.getGameId());

                try {
                    Thread.sleep(gameDuration * 1000L); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); 
                    break;
                }
                
                // --- TEMPO SCADUTO: Salvataggio statistiche per chi non ha finito ---
                // Iteriamo su tutte le chiavi del selettore per trovare i client
                for (SelectionKey key : selector.keys()) {
                    if (key.isValid() && key.attachment() instanceof ClientSession) {
                        ClientSession session = (ClientSession) key.attachment();
                        
                        // Se l'utente è loggato e NON ha finito la partita (non ha vinto né perso)
                        if (session.isLoggedIn() && !session.isGameFinished()) {
                            // Aggiorniamo le stats (Time Out)
                            UserManager.getInstance().updateStatsTimeOut(session.getUsername());
                        }
                    }
                }

                broadcastGameUpdate("TEMPO SCADUTO! Fine partita " + g.getGameId());
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
    
    // Metodo per inviare messaggi a TUTTI i client connessi (Async TCP notification)
    public void broadcastGameUpdate(String message) {
        ServerResponse resp = new ServerResponse();
        resp.status = "EVENT"; // Status speciale per eventi
        resp.message = message;
        // Se la partita è finita, potresti mettere qui resp.isFinished = true
        
        String json = new Gson().toJson(resp);
        ByteBuffer buffer = ByteBuffer.wrap(json.getBytes());

        for (SocketChannel client : connectedClients) {
            if (client.isOpen()) {
                try {
                    // Duplichiamo il buffer per ogni client (NIO requirement)
                    client.write(buffer.duplicate());
                } catch (IOException e) {
                    // Ignora errori di scrittura su broadcast
                }
            }
        }
    }
    
    // Helper per rimuovere client dalla lista
    public void removeClient(SocketChannel client) {
        connectedClients.remove(client);
        try { client.close(); } catch (IOException e) {}
    }

    private void disconnectClient(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        removeClient(client);
        key.cancel();
        System.out.println("Client disconnesso forzatamente.");
    }

    private void acceptConnection(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        System.out.println("Nuovo client: " + client.getRemoteAddress());
        
        // AGGIUNTO ALLA LISTA BROADCAST
        connectedClients.add(client);
        
        ClientSession session = new ClientSession();
        client.register(selector, SelectionKey.OP_READ, session);
    }
    
    // ... startConsoleListener, closeServer, getTimeLeft ... (copiali dalla versione precedente o lascia invariati)
    
    public int getTimeLeft() {
        long elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        int remaining = (int) (gameDuration - elapsed);
        return Math.max(0, remaining);
    }

    private void startConsoleListener() {
        new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (running) {
                    if (scanner.hasNextLine() && "exit".equalsIgnoreCase(scanner.nextLine().trim())) {
                        running = false; selector.wakeup(); break;
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void closeServer() {
        try { if (selector != null) selector.close(); System.exit(0); } catch (IOException e) {}
    }

    public synchronized void setCurrentGame(Game g) { this.currentGame = g; }
    public synchronized Game getCurrentGame() { return currentGame; }
}