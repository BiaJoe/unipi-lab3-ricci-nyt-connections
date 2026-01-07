package client.network;

import client.ui.ClientRenderer;
import com.google.gson.Gson;
import utils.ServerResponse;
import java.io.IOException;
import java.net.DatagramPacket;

public class UdpListener implements Runnable {
    private final NetworkManager net;
    private final ClientRenderer ui;
    private final int bufferSize;
    private final Gson gson = new Gson();

    public UdpListener(NetworkManager net, ClientRenderer ui, int bufferSize) {
        this.net = net;
        this.ui = ui;
        this.bufferSize = bufferSize;
    }

    @Override
    public void run() {
        byte[] buf = new byte[bufferSize];
        
        while (net.getUdpSocket() != null && !net.getUdpSocket().isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                net.getUdpSocket().receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength());
                ServerResponse resp = gson.fromJson(json, ServerResponse.class);

                // Controllo robusto su objectCode
                if ("RES_EVENT".equals(resp.objectCode) && resp.message != null) {
                    ui.showNotification(resp.message);
                }

            } catch (IOException e) {
                if (net.getUdpSocket().isClosed()) break;
                ui.showError("Errore UDP: " + e.getMessage());
            } catch (Exception e) {
                // Ignora JSON malformati
            }
        }
    }
}