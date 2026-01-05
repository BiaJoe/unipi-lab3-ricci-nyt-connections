package server;

import server.models.ClientSession;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
    private GameScheduler gameScheduler;
    private DatagramSocket udpSocket;

    public static void main(String[] args) {
        try {
            ServerConfig.load("server.properties");
            UserManager.getInstance(); 
            GameManager.getInstance(); 
            new ServerMain().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(ServerConfig.PORT));
        serverChannel.configureBlocking(false);

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        try { udpSocket = new DatagramSocket(); } catch (Exception e) {}

        System.out.println("Server TCP avviato sulla porta " + ServerConfig.PORT);
        System.out.println("Sistema notifiche UDP pronto.");

        gameScheduler = new GameScheduler(this);
        new Thread(gameScheduler).start();

        startConsoleListener();

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
    
    public void sendUdpBroadcast(String jsonMessage) {
        if (udpSocket == null || udpSocket.isClosed()) return;
        byte[] data = jsonMessage.getBytes();
        for (ClientSession session : getAllSessions()) {
            if (session.isLoggedIn() && session.getUdpPort() > 0 && session.getClientAddress() != null) {
                try {
                    DatagramPacket packet = new DatagramPacket(data, data.length, session.getClientAddress(), session.getUdpPort());
                    udpSocket.send(packet);
                } catch (IOException e) {}
            }
        }
    }

    public void broadcast(BiFunction<ClientSession, SelectionKey, String> msgGenerator) {
        if (selector == null) return;
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ClientSession) {
                ClientSession session = (ClientSession) key.attachment();
                String json = msgGenerator.apply(session, key);
                if (json != null) sendResponse(key, json);
            }
        }
    }
    
    // --- IL METODO CHE MANCAVA ---
    public void sendResponse(SelectionKey key, String json) {
        if (key == null || !key.isValid()) return;
        try { 
            // AGGIUNGO IL \n PER IL PROTOCOLLO
            String messageWithTerminator = json + "\n";
            ((SocketChannel)key.channel()).write(ByteBuffer.wrap(messageWithTerminator.getBytes())); 
        } catch (IOException e) {
            disconnectClient(key);
        }
    }

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
        ClientSession session = new ClientSession();
        session.setClientAddress(client.socket().getInetAddress());
        client.register(selector, SelectionKey.OP_READ, session);
    }

    public void disconnectClient(SelectionKey key) {
        try { key.channel().close(); } catch (IOException e) {}
        key.cancel();
        System.out.println("Client disconnesso.");
    }
    
    private void startConsoleListener() {
        new Thread(() -> {
            try (Scanner s = new Scanner(System.in)) {
                while (running) {
                    if (s.hasNextLine() && "exit".equalsIgnoreCase(s.nextLine().trim())) {
                        running = false; gameScheduler.stop(); if (selector != null) selector.wakeup(); break;
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    private void closeServer() {
        try { if (selector != null) selector.close(); if (udpSocket != null) udpSocket.close(); System.exit(0); } catch (IOException e) {}
    }
}