package server.network;

import server.ServerConfig;
import server.models.ClientSession;
import server.ui.ServerLogger;
import utils.ServerResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkService {
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private final ExecutorService workerPool;
    private final UdpSender udpSender;
    private volatile boolean running = true;

    // FIX CONCORRENZA: Manteniamo le sessioni in un Set Thread-Safe separato dal Selector
    private final Set<ClientSession> activeSessions = ConcurrentHashMap.newKeySet();

    public NetworkService() {
        this.workerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.udpSender = new UdpSender();
    }

    public void init() throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(ServerConfig.PORT));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        ServerLogger.info("Server inizializzato su porta TCP " + ServerConfig.PORT);
    }

    public void start() {
        ServerLogger.info("Network Loop avviato. In attesa di connessioni...");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                if (selector == null) return;
                selector.select(); 
                if (!running) break;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) acceptConnection();
                    else if (key.isReadable()) TcpReader.readFromClient(key, this);
                }
            }
        } catch (IOException e) {
            ServerLogger.error("Errore nel loop di rete: " + e.getMessage());
        }
    }

    public void stop() {
        this.running = false;
        if (selector != null) selector.wakeup();
        if (workerPool != null) workerPool.shutdown();
        ServerLogger.info("NetworkService fermato.");
    }

    private void acceptConnection() throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        
        ClientSession session = new ClientSession();
        SelectionKey key = client.register(selector, SelectionKey.OP_READ);
        
        key.attach(session);
        session.setSelectionKey(key); 
        
        // AGGIUNTA SICURA
        activeSessions.add(session);

        ServerLogger.info("Nuova connessione: " + client.getRemoteAddress());
    }

    public void submitTask(Runnable task) {
        if (!workerPool.isShutdown()) workerPool.execute(task);
    }

    public void disconnectClient(SelectionKey key) {
        try {
            // RIMOZIONE SICURA
            if (key.attachment() instanceof ClientSession) {
                activeSessions.remove((ClientSession) key.attachment());
            }
            
            key.channel().close();
            key.cancel();
            ServerLogger.info("Client disconnesso.");
        } catch (IOException e) {
            ServerLogger.error("Errore disconnessione: " + e.getMessage());
        }
    }

    // ORA QUESTO METODO È THREAD-SAFE per il GameScheduler
    public Collection<ClientSession> getAllSessions() {
        return Collections.unmodifiableCollection(activeSessions);
    }

    // --- INVIO RISPOSTE ---

    public void sendUdpResponse(ServerResponse.Event event) {
        udpSender.broadcast(event, getAllSessions());
    }

    public void sendTcpResponse(ClientSession session, String json) {
        if (session != null && session.getSelectionKey() != null && session.getSelectionKey().isValid()) {
            TcpWriter.send(session.getSelectionKey(), json, this);
        }
    }
    
    public void sendTcpResponse(SelectionKey key, String json) {
        TcpWriter.send(key, json, this);
    }
}