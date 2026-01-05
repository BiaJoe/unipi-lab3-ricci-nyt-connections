package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import utils.ServerResponse; 

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
    public static boolean testMode;

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
            
            // Lettura flag test mode (default false)
            testMode = Boolean.parseBoolean(prop.getProperty("testMode", "false"));
            
            if (testMode) {
                System.out.println("!!! MODALITÀ TEST ATTIVA: Caricamento Connections_Test.json !!!");
                dataFilePath = "data/Connections_Test.json";
            } else {
                dataFilePath = prop.getProperty("dataFilePath", "data/Connections_Data.json");
            }

            usersFilePath = prop.getProperty("usersFilePath", "data/Users.json");
            gameDuration = Integer.parseInt(prop.getProperty("gameDuration", "60"));
            maxErrors = Integer.parseInt(prop.getProperty("maxErrors", "4"));
        }
    }

    public void start() {
        try {
            // 1. INIZIALIZZAZIONE RETE (Prima di far partire i thread!)
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port)); 
            serverChannel.configureBlocking(false);
            
            selector = Selector.open(); // Inizializzato QUI per evitare NullPointer nel gameLoop
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            System.out.println("Server avviato sulla porta " + port);

            // 2. AVVIO THREAD AUSILIARI
            Thread gameThread = new Thread(this::gameLoop);
            gameThread.start();
            
            startConsoleListener();

            // 3. LOOP DI RETE PRINCIPALE
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
                        disconnectClient(key); 
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeServer();
        }
    }

    // --- GAME LOOP ---
    private void gameLoop() {
        try (JsonReader reader = new JsonReader(new FileReader(dataFilePath))) {
            Gson gson = new Gson();
            reader.beginArray();
            
            while (reader.hasNext() && running) { 
                Game g = gson.fromJson(reader, Game.class);
                
                // --- FASE 1: INIZIO NUOVA PARTITA ---
                setCurrentGame(g);
                this.gameStartTime = System.currentTimeMillis(); 
                
                // Reset sessioni e PUSH nuove parole
                if (selector != null) {
                    for (SelectionKey key : selector.keys()) {
                        if (key.isValid() && key.attachment() instanceof ClientSession) {
                            ClientSession session = (ClientSession) key.attachment();
                            session.resetGameStatus(); // Reset per tutti
                            
                            // Se loggato, invia subito la nuova griglia!
                            if (session.isLoggedIn()) {
                                ServerResponse resp = new ServerResponse();
                                resp.status = "OK";
                                resp.message = "NUOVA PARTITA INIZIATA!";
                                resp.gameInfo = RequestProcessor.buildGameInfo(g, session, this);
                                sendJsonToClient((SocketChannel)key.channel(), gson.toJson(resp));
                            }
                        }
                    }
                }

                System.out.println("--- NUOVA PARTITA ID: " + g.getGameId() + " ---");
                
                // Attesa durata partita
                try {
                    Thread.sleep(gameDuration * 1000L); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); 
                    break;
                }
                
                // --- FASE 2: FINE PARTITA (TEMPO SCADUTO) ---
                if (selector != null) {
                    for (SelectionKey key : selector.keys()) {
                        if (key.isValid() && key.attachment() instanceof ClientSession) {
                            ClientSession session = (ClientSession) key.attachment();
                            
                            if (session.isLoggedIn()) {
                                // Aggiorna stats Time Out se non aveva finito
                                if (!session.isGameFinished()) {
                                    UserManager.getInstance().updateStatsTimeOut(session.getUsername());
                                }
                                
                                // PUSH Soluzione e Risultato finale
                                ServerResponse resp = new ServerResponse();
                                resp.status = "EVENT";
                                resp.message = "TEMPO SCADUTO";
                                resp.isFinished = true;
                                resp.solution = new java.util.ArrayList<>();
                                for (Group gr : g.getGroups()) {
                                    resp.solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
                                }
                                sendJsonToClient((SocketChannel)key.channel(), gson.toJson(resp));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
    
    // Helper per inviare JSON diretto a un client specifico
    private void sendJsonToClient(SocketChannel client, String json) {
        if (client == null || !client.isOpen()) return;
        try {
            client.write(ByteBuffer.wrap(json.getBytes()));
        } catch (IOException e) {
            // Ignora errori su socket chiusi durante il broadcast
        }
    }
    
    // Helper statistiche
    public static class GameStats {
        public int active;
        public int finished;
        public int won;
    }

    public GameStats calculateCurrentGameStats() {
        GameStats stats = new GameStats();
        if (selector == null) return stats;

        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ClientSession) {
                ClientSession session = (ClientSession) key.attachment();
                if (session.isLoggedIn()) {
                    if (session.isGameFinished()) {
                        stats.finished++;
                        if (session.getScore() == 4) { // Score 4 = Vittoria
                            stats.won++;
                        }
                    } else {
                        stats.active++;
                    }
                }
            }
        }
        return stats;
    }

    // Broadcast generico (opzionale, usato per messaggi sistema)
    public void broadcastGameUpdate(String message) {
        ServerResponse resp = new ServerResponse();
        resp.status = "EVENT"; 
        resp.message = message;
        
        String json = new Gson().toJson(resp);
        ByteBuffer buffer = ByteBuffer.wrap(json.getBytes());

        for (SocketChannel client : connectedClients) {
            if (client.isOpen()) {
                try {
                    client.write(buffer.duplicate());
                } catch (IOException e) {}
            }
        }
    }
    
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
        connectedClients.add(client);
        ClientSession session = new ClientSession();
        client.register(selector, SelectionKey.OP_READ, session);
    }
    
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
                        running = false; 
                        if (selector != null) selector.wakeup(); 
                        break;
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