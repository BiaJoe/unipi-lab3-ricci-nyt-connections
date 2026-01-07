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
import java.util.Collections; // Importante
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NetworkService {
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private final ExecutorService workerPool;
    private final UdpSender udpSender;
    private volatile boolean running = true;

    public NetworkService() {
        this.workerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.udpSender = new UdpSender();
    }

    // --- FASE 1: Inizializzazione (Non bloccante) ---
    public void init() throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        
        // Uso ServerConfig.PORT statico
        serverSocket.bind(new InetSocketAddress(ServerConfig.PORT));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        ServerLogger.info("Server inizializzato su porta TCP " + ServerConfig.PORT);
    }

    // --- FASE 2: Loop Principale (Bloccante) ---
    public void start() {
        ServerLogger.info("Network Loop avviato. In attesa di connessioni...");
        
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                selector.select();
                
                if (!running) break;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        acceptConnection();
                    } else if (key.isReadable()) {
                        TcpReader.readFromClient(key, this);
                    }
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

        ServerLogger.info("Nuova connessione: " + client.getRemoteAddress());
    }

    public void submitTask(Runnable task) {
        if (!workerPool.isShutdown()) {
            workerPool.execute(task);
        }
    }

    public void disconnectClient(SelectionKey key) {
        try {
            key.channel().close();
            key.cancel();
            ServerLogger.info("Client disconnesso.");
        } catch (IOException e) {
            ServerLogger.error("Errore disconnessione: " + e.getMessage());
        }
    }

    // FIX: Aggiunto controllo null-safety per evitare crash se chiamato troppo presto
    public Collection<ClientSession> getAllSessions() {
        if (selector == null) {
            return Collections.emptyList();
        }
        return selector.keys().stream()
                .filter(k -> k.attachment() instanceof ClientSession)
                .map(k -> (ClientSession) k.attachment())
                .collect(Collectors.toList());
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