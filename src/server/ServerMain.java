package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class ServerMain {
    private static final int PORT = 8080;
    private static final String DATA_FILE = "data/Connections_Data.json";
    
    // Stato del server
    private Game currentGame;
    private Selector selector;
    
    // Flag volatile per gestire l'arresto da thread diversi
    private volatile boolean running = true;

    public static void main(String[] args) {
        new ServerMain().start();
    }

    public void start() {
        // 1. Avvio thread caricamento partite
        Thread gameThread = new Thread(this::gameLoop);
        gameThread.start();

        // 2. Avvio thread ascolto Console (per il comando exit)
        startConsoleListener();

        // 3. Avvio Server NIO
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            System.out.println("Server avviato sulla porta " + PORT);
            System.out.println("Digita 'exit' per chiudere il server.");

            // Loop principale
            while (running) {
                // select() blocca, ma può essere svegliato da selector.wakeup()
                selector.select(); 
                
                // Se siamo stati svegliati perché running è false, usciamo
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
                        // Gestione disconnessione improvvisa durante accept/read
                        key.cancel();
                        try { key.channel().close(); } catch (IOException ex) {}
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Chiusura pulita delle risorse
            closeServer();
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
                        // FONDAMENTALE: Sveglia il main thread che è bloccato su selector.select()
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
            // Forza la chiusura della JVM (utile per uccidere anche il thread gameLoop che sta dormendo)
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

    private void gameLoop() {
        try (JsonReader reader = new JsonReader(new FileReader(DATA_FILE))) {
            Gson gson = new Gson();
            reader.beginArray();
            while (reader.hasNext() && running) { // Controlliamo running anche qui
                Game g = gson.fromJson(reader, Game.class);
                setCurrentGame(g);
                System.out.println("Nuova partita caricata ID: " + g.getGameId());
                
                // Dorme 60 secondi, o finché non viene interrotto
                try {
                    Thread.sleep(60000); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Ripristina stato interrupted
                    break;
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace(); // Stampa errore solo se non stiamo chiudendo
        }
    }

    public synchronized void setCurrentGame(Game g) { this.currentGame = g; }
    public synchronized Game getCurrentGame() { return currentGame; }
}