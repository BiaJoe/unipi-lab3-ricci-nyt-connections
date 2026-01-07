package server.network;

import com.google.gson.Gson;
import server.models.ClientSession;
import server.ui.ServerLogger;
import utils.ServerResponse;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection; // MODIFICATO: Importa Collection invece di List

public class UdpSender {
    private final Gson gson = new Gson();

    // MODIFICATO: Ora accetta Collection<ClientSession> invece di List
    public void broadcast(ServerResponse response, Collection<ClientSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return;

        String json = gson.toJson(response);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            for (ClientSession session : sessions) {
                // Inviamo solo agli utenti loggati che hanno una porta UDP valida (> 0)
                if (session.isLoggedIn() && session.getUdpPort() > 0) {
                    try {
                        // L'indirizzo IP lo prendiamo dal canale TCP associato
                        InetAddress address = session.getSelectionKey().channel() instanceof java.nio.channels.SocketChannel 
                                ? ((java.nio.channels.SocketChannel) session.getSelectionKey().channel()).socket().getInetAddress()
                                : InetAddress.getLocalHost(); // Fallback (es. test locale)

                        DatagramPacket packet = new DatagramPacket(
                            data, 
                            data.length, 
                            address, 
                            session.getUdpPort()
                        );
                        
                        socket.send(packet);
                        
                    } catch (Exception e) {
                        ServerLogger.error("Errore invio UDP a " + session.getUsername() + ": " + e.getMessage());
                    }
                }
            }
            ServerLogger.info("Broadcast UDP inviato: " + response.message);
        } catch (IOException e) {
            ServerLogger.error("Errore apertura socket UDP sender: " + e.getMessage());
        }
    }
}