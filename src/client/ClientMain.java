package client;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

public class ClientMain {
    // Configurazione
    public static final String configFile = "client.properties";
    public static String serverAddress;
    public static int serverPort;

    // Variabili per NIO
    private static SocketChannel clientChannel;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try {
            // 1. Carico configurazione
            readConfig();
            
            // 2. Connessione NIO
            clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress(serverAddress, serverPort));

            System.out.println("--- CLIENT NIO CONNESSO A " + serverAddress + ":" + serverPort + " ---");
            System.out.println("Comandi: login, info, submit, exit");

            // 3. Avvio listener
            new Thread(new ServerListener()).start();

            // 4. Input Loop
            game();

        } catch (Exception e) {
            System.err.println("Errore: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void readConfig() throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);

            serverAddress = prop.getProperty("serverAddress");
            serverPort = Integer.parseInt(prop.getProperty("serverPort"));
        }
    }

    public static void game() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break; 
                
                String command = scanner.nextLine();

                if ("exit".equalsIgnoreCase(command)) {
                    clientChannel.close();
                    break;
                }

                ClientRequest req = new ClientRequest();

                if ("login".equalsIgnoreCase(command)) {
                    req.operation = "login";
                    System.out.print("Username: ");
                    req.username = scanner.nextLine(); 
                } 
                else if ("info".equalsIgnoreCase(command)) {
                    req.operation = "get_game"; 
                }
                else if ("submit".equalsIgnoreCase(command)) {
                    req.operation = "propose_solution";
                    req.words = Arrays.asList("CANE", "GATTO", "SOLE", "MARE");
                }
                else {
                    req.operation = command;
                }

                sendRequest(req);
            }
        } catch (IOException e) {
            System.err.println("Errore I/O nel game loop: " + e.getMessage());
        }
    }

    private static void sendRequest(ClientRequest req) {
        try {
            String json = gson.toJson(req);
            ByteBuffer buffer = ByteBuffer.wrap(json.getBytes());
            clientChannel.write(buffer);
        } catch (IOException e) {
            System.err.println("Errore invio: " + e.getMessage());
        }
    }

    static class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(2048);
                while (clientChannel.isOpen()) {
                    int bytesRead = clientChannel.read(buffer);
                    if (bytesRead == -1) {
                        System.out.println("\nServer chiuso.");
                        System.exit(0);
                    }
                    if (bytesRead > 0) {
                        buffer.flip();
                        String jsonResponse = new String(buffer.array(), 0, bytesRead);
                        try {
                            ServerResponse resp = gson.fromJson(jsonResponse, ServerResponse.class);
                            System.out.println("\n[SERVER]: " + resp.message);
                            if (resp.data != null) System.out.println("Data: " + resp.data);
                        } catch (Exception e) {
                            System.out.println("\n[RAW MSG]: " + jsonResponse);
                        }
                        System.out.print("> "); 
                        buffer.clear();
                    }
                }
            } catch (IOException e) {
                // Canale chiuso
            }
        }
    }
}