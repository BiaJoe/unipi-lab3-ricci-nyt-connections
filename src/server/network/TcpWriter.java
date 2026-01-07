package server.network;

import server.models.ClientSession;
import server.ui.ServerLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class TcpWriter {

    public static void send(SelectionKey key, String rawJson, NetworkService service) {
        if (key == null || !key.isValid()) return;

        // Recupera info destinatario
        ClientSession session = (ClientSession) key.attachment();
        String recipient = (session != null && session.getUsername() != null) 
                           ? session.getUsername() : "Anonimo";

        // 1. LOGGING (Delega al Logger la formattazione)
        ServerLogger.logTraffic("[TCP OUT] -> " + recipient, rawJson);

        // 2. INVIO RETE (Usa rawJson compatto + newline)
        try {
            String messageWithTerminator = rawJson + "\n";
            ByteBuffer buffer = ByteBuffer.wrap(messageWithTerminator.getBytes());
            ((SocketChannel) key.channel()).write(buffer);
        } catch (IOException e) {
            service.disconnectClient(key);
        }
    }
}