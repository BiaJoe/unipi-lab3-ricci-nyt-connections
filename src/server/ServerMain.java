package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

public class ServerMain {
    // Configurazione
    public static final String configFile = "server.properties";
    public static int port;
    public static String dataFilePath;
    public static int gameDuration;

    // Stato del server
    private Game currentGame;
    private Selector selector;
    private volatile boolean running = true;

    public static void main(String[] args) {
        // 0. Caricamento Configurazione
        try {
            readConfig();
        } catch (IOException e) {
            System.err.println("Errore caricamento " + configFile + ": " + e.getMessage());
            System.exit(1);
        }
        
        // Avvio istanza
        new ServerMain().start();
    }

    public static void readConfig() throws IOException {
        // Usiamo FileInputStream per leggere dalla cartella root del progetto
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);

            port = Integer.parseInt(prop.getProperty("port"));
            dataFilePath = prop.getProperty("dataFilePath");
            gameDuration = Integer.parseInt(prop.getProperty("gameDuration"));
        }
    }

    public void start() {
        // 1. Avvio thread caricamento partite
        Thread gameThread = new Thread(this::gameLoop);
        gameThread.start();

        // 2. Avvio thread ascolto Console
        startConsoleListener();

        // 3. Avvio Server NIO
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            // Uso la variabile statica caricata da readConfig
            serverChannel.bind(new InetSocketAddress(port)); 
            serverChannel.configureBlocking(false);
            
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            System.out.println("Server avviato sulla porta " + port);
            System.out.println("Durata round: " + gameDuration + "s");
            System.out.println("Digita 'exit' per chiudere il server.");

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
                        if (key.isAcceptable()) {
                            acceptConnection(serverChannel);
                        } else if (key.isReadable()) {
                            ClientHandler.handleRead(key, this);
                        }
                    } catch (IOException e) {
                        key.cancel();
                        try { key.channel().close(); } catch (IOException ex) {}
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeServer();
        }
    }

    // --- Metodi invariati ---
    private void gameLoop() {
        try (JsonReader reader = new JsonReader(new FileReader(dataFilePath))) {
            Gson gson = new Gson();
            reader.beginArray();
            while (reader.hasNext() && running) { 
                Game g = gson.fromJson(reader, Game.class);
                setCurrentGame(g);
                System.out.println("Nuova partita caricata ID: " + g.getGameId());
                
                try {
                    // Uso la variabile statica gameDuration
                    Thread.sleep(gameDuration * 1000L); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); 
                    break;
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace(); 
        }
    }

    private void startConsoleListener() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(command.trim())) {
                        System.out.println("Chiusura server in corso...");
                        running = false;
                        selector.wakeup();
                        break;
                    }
                }
            }
        }).start();
    }

    private void closeServer() {
        try {
            if (selector != null) selector.close();
            System.out.println("Server chiuso correttamente.");
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptConnection(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        System.out.println("Nuovo client: " + client.getRemoteAddress());
        ClientSession session = new ClientSession();
        client.register(selector, SelectionKey.OP_READ, session);
    }

    public synchronized void setCurrentGame(Game g) { this.currentGame = g; }
    public synchronized Game getCurrentGame() { return currentGame; }
}