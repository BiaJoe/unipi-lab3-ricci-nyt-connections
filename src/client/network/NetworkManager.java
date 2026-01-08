package client.network;

import client.ClientConfig;
import utils.ClientRequest;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Invia messaggi di richiesta al server
 */
public class NetworkManager {
    private SocketChannel tcpChannel;
    private DatagramSocket udpSocket;
    private final ClientConfig config;
    private final Gson gson = new Gson();

    public NetworkManager(ClientConfig config) {
        this.config = config;
    }

    public void connect() throws IOException {
        udpSocket = new DatagramSocket();
        
        tcpChannel = SocketChannel.open();
        tcpChannel.socket().connect(
            new InetSocketAddress(config.serverAddress, config.serverPort), 
            config.connectTimeout
        );
        tcpChannel.configureBlocking(true); 
    }

    // gestione di request sollevate dal Command Processor
    public void sendRequest(ClientRequest req) throws IOException {
        if (tcpChannel == null || !tcpChannel.isOpen()) {
            throw new IOException("Non connesso al server.");
        }
        
        String json = gson.toJson(req);
        String message = json + "\n";
        tcpChannel.write(ByteBuffer.wrap(message.getBytes()));
    }

    // evito chiusure multiple
    public synchronized void close() {
        try { 
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close(); 
            }
        } catch (Exception e) {}
        
        try { 
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close(); 
            }
        } catch (Exception e) {}
    }

    public SocketChannel getTcpChannel() { return tcpChannel; }
    public DatagramSocket getUdpSocket() { return udpSocket; }
    public int getLocalUdpPort() { return (udpSocket != null) ? udpSocket.getLocalPort() : 0; }
}

