package server;

import server.models.ClientSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.function.BiFunction;

public class ServerMain {
    private Selector selector;
    private volatile boolean running = true;
    
    // Rimosso currentGame e timer -> Ora sono in GameManager
    private GameScheduler gameScheduler;

    public static void main(String[] args) {
        try {
            ServerConfig.load("server.properties");
            UserManager.getInstance(); 
            // Inizializza il GameManager
            GameManager.getInstance(); 
            new ServerMain().start();
        } catch (IOException e) {
            System.err.println("Errore avvio server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(ServerConfig.PORT));
        serverChannel.configureBlocking(false);

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server avviato sulla porta " + ServerConfig.PORT);

        // Avvio Thread Gioco
        gameScheduler = new GameScheduler(this);
        new Thread(gameScheduler).start();

        startConsoleListener();

        // --- NETWORK LOOP ---
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
        closeServer();
    }

    // --- METODI DI RETE ---

    public void broadcast(BiFunction<ClientSession, SelectionKey, String> msgGenerator) {
        if (selector == null) return;
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ClientSession) {
                ClientSession session = (ClientSession) key.attachment();
                String json = msgGenerator.apply(session, key);
                if (json != null) sendJson(key, json);
            }
        }
    }
    
    // Metodo per permettere al GameManager di calcolare le stats
    public List<ClientSession> getAllSessions() {
        List<ClientSession> sessions = new ArrayList<>();
        if (selector == null) return sessions;
        
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ClientSession) {
                sessions.add((ClientSession) key.attachment());
            }
        }
        return sessions;
    }

    private void acceptConnection(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        System.out.println("Nuovo client: " + client.getRemoteAddress());
        client.register(selector, SelectionKey.OP_READ, new ClientSession());
    }

    public void disconnectClient(SelectionKey key) {
        try { key.channel().close(); } catch (IOException e) {}
        key.cancel();
        System.out.println("Client disconnesso.");
    }
    
    private void sendJson(SelectionKey key, String json) {
        try { ((SocketChannel)key.channel()).write(ByteBuffer.wrap(json.getBytes())); } catch (IOException e) {}
    }

    private void startConsoleListener() {
        new Thread(() -> {
            // FIX: Try-with-resources per chiudere lo scanner
            try (Scanner s = new Scanner(System.in)) {
                while (running) {
                    // hasNextLine blocca finché non c'è input
                    if (s.hasNextLine()) {
                        String line = s.nextLine().trim();
                        if ("exit".equalsIgnoreCase(line)) {
                            running = false;
                            gameScheduler.stop();
                            if (selector != null) selector.wakeup(); // Sblocca la select()
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignora errori chiusura scanner
            }
        }).start();
    }

    private void closeServer() {
        try { if (selector != null) selector.close(); System.exit(0); } catch (IOException e) {}
    }
}