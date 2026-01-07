package server.network;

import server.handlers.ClientRequestHandler;
import server.models.ClientSession;
import server.ui.ServerLogger;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PacketHandler {

    // Nota: Rimosso 'new ClientRequestHandler()' perché ora i metodi sono statici
    
    public static void processReceivedData(ClientSession session, byte[] data, NetworkService netService, SelectionKey key) {
        // 1. Accoda i nuovi dati al buffer della sessione
        session.getBuffer().append(new String(data));
        
        // 2. Loop per processare tutti i messaggi completi (che finiscono con \n)
        while (true) {
            String currentBuffer = session.getBuffer().toString();
            int newlineIndex = currentBuffer.indexOf('\n');
            
            if (newlineIndex == -1) break;
            
            // 3. Estrai il messaggio JSON pulito
            String jsonMessage = currentBuffer.substring(0, newlineIndex).trim();
            session.getBuffer().delete(0, newlineIndex + 1);
            
            if (!jsonMessage.isEmpty()) {
                dispatchMessage(jsonMessage, session, netService, key);
            }
        }
    }

    private static void dispatchMessage(String json, ClientSession session, NetworkService netService, SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        String clientIp = client.socket().getInetAddress().toString();

        ServerLogger.logJsonReceived(clientIp, json);
        
        // 4. Invio al Thread Pool tramite il metodo wrapper di NetworkService
        netService.submitTask(() -> {
            try {
                // Chiamata statica a ClientRequestHandler
                // Passiamo 'session' perché serve per Login/Register/Info
                String response = ClientRequestHandler.handleRequest(json, session);
                
                if (response != null) {
                    netService.sendTcpResponse(key, response);
                }
            } catch (Exception e) {
                ServerLogger.error("Errore Worker per " + clientIp + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}