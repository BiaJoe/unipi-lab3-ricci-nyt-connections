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
            // Disconnessione improvvisa (Connection reset)
            server.disconnectClient(key);
            return;
        }

        // -1 indica che il client ha chiuso la connessione in modo ordinato (EOF)
        if (bytesRead == -1) {
            server.disconnectClient(key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            
            // 1. Accumulo i dati nel buffer della sessione (gestisce frammentazione TCP)
            session.getBuffer().append(new String(data));
            
            String fullMessage = session.getBuffer().toString().trim();

            // 2. Controllo basilare fine messaggio JSON
            if (fullMessage.endsWith("}")) { 
                System.out.println("[RECV] " + client.socket().getInetAddress() + ": " + fullMessage);
                
                // 3. Elaborazione comando
                String response = RequestProcessor.process(fullMessage, session, server);
                
                // 4. Invio risposta (se prevista)
                if (response != null) {
                    try {
                        client.write(ByteBuffer.wrap(response.getBytes()));
                    } catch (IOException e) {
                        server.disconnectClient(key);
                        return;
                    }
                }
                
                // 5. Reset del buffer per il prossimo comando
                session.getBuffer().setLength(0);
            }
        }
    }
}