package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ClientHandler {
    
    public static void handleRead(SelectionKey key, ServerMain server) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();
        
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            // Disconnessione improvvisa
            server.removeClient(client);
            key.cancel();
            return;
        }

        if (bytesRead == -1) {
            System.out.println("Client disconnesso: " + client.getRemoteAddress());
            server.removeClient(client); // Rimuovi dalla lista broadcast
            key.cancel();
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String chunk = new String(data);
            
            session.getBuffer().append(chunk);
            
            String fullMessage = session.getBuffer().toString();
            // Controllo semplificato JSON
            if (fullMessage.trim().endsWith("}")) { 
                System.out.println("Ricevuto: " + fullMessage);
                
                String response = RequestProcessor.process(fullMessage, session, server);
                
                if (response != null) {
                    try {
                        client.write(ByteBuffer.wrap(response.getBytes()));
                    } catch (IOException e) {
                        server.removeClient(client);
                        key.cancel();
                    }
                }
                session.getBuffer().setLength(0);
            }
        }
    }
}