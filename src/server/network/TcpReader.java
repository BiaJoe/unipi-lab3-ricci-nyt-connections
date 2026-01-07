package server.network;

import server.models.ClientSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class TcpReader {

    // Dimensione buffer di lettura
    private static final int BUFFER_SIZE = 4096;

    public static void readFromClient(SelectionKey key, NetworkService netService) {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();
        
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead;

        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            // Eccezione IO (es. connessione resettata) -> disconnetti
            netService.disconnectClient(key);
            return;
        }

        if (bytesRead == -1) {
            // End of stream (il client ha chiuso) -> disconnetti
            netService.disconnectClient(key);
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            // Passiamo i dati grezzi al gestore del protocollo
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            
            // Deleghiamo la logica di "assemblaggio pacchetti" all'altra classe
            PacketHandler.processReceivedData(session, data, netService, key);
        }
    }
}