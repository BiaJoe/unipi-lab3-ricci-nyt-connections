package server;

import server.models.ClientSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ClientHandler {
    
    public static void handleRead(SelectionKey key, ServerMain server) {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();
        
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int bytesRead;

        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            server.disconnectClient(key);
            return;
        }

        if (bytesRead == -1) {
            server.disconnectClient(key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            
            // 1. Accumula i dati nel buffer della sessione
            session.getBuffer().append(new String(data));
            
            // 2. Processa TUTTI i messaggi completi (che terminano con \n)
            while (true) {
                String currentBuffer = session.getBuffer().toString();
                int newlineIndex = currentBuffer.indexOf('\n');
                
                if (newlineIndex == -1) {
                    break; // Nessun messaggio completo, aspetto altri dati
                }
                
                // Estrai il messaggio (senza il \n)
                String message = currentBuffer.substring(0, newlineIndex).trim();
                
                // Rimuovi il messaggio processato dal buffer
                session.getBuffer().delete(0, newlineIndex + 1);
                
                if (!message.isEmpty()) {
                    System.out.println("[RECV] " + client.socket().getInetAddress() + ": " + message);
                    
                    String response = RequestProcessor.process(message, session, server);
                    
                    if (response != null) {
                        // Importante: le risposte devono essere inviate tramite ServerMain.sendJson
                        // che ora aggiungerà il \n (vedi modifica successiva)
                        server.sendResponse(key, response);
                    }
                }
            }
        }
    }
}