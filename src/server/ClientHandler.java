package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ClientHandler {
    
    public static void handleRead(SelectionKey key, ServerMain server) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();
        
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            System.out.println("Client disconnesso: " + client.getRemoteAddress());
            client.close();
            key.cancel();
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String chunk = new String(data);
            
            // Aggiungiamo al buffer della sessione (gestione frammentazione)
            session.getBuffer().append(chunk);
            
            // Controllo grezzo fine messaggio (assumiamo che il client mandi \n alla fine o JSON completo)
            // In un progetto reale qui si controllano le parentesi graffe bilanciate
            String fullMessage = session.getBuffer().toString();
            if (fullMessage.trim().endsWith("}")) { 
                
                System.out.println("Ricevuto: " + fullMessage);
                
                // Processiamo
                String response = RequestProcessor.process(fullMessage, session, server);
                
                // Rispondiamo
                if (response != null) {
                    ByteBuffer out = ByteBuffer.wrap((response + "\n").getBytes());
                    client.write(out);
                }
                
                // Puliamo il buffer per il prossimo comando
                session.getBuffer().setLength(0);
            }
        }
    }
}