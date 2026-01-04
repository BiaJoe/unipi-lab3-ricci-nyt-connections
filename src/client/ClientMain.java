package client;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Scanner;

public class ClientMain {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8080;
    private static final Gson gson = new Gson();
    
    // Canale NIO
    private static SocketChannel clientChannel;

    public static void main(String[] args) {
        try {
            // 1. Connessione NIO
            clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress(HOST, PORT));

            System.out.println("--- CLIENT NIO CONNESSO ---");
            System.out.println("Comandi: login, info, submit, exit");

            // 2. Avviamo il listener
            new Thread(new ServerListener()).start();

            // 3. FIX: TRY-WITH-RESOURCES per lo Scanner
            // Lo dichiariamo dentro le parentesi tonde del try.
            // Java lo chiuderà automaticamente alla fine delle parentesi graffe.
            try (Scanner scanner = new Scanner(System.in)) {
                
                while (true) {
                    System.out.print("> ");
                    
                    // Controllo se c'è una linea (evita eccezioni se fai CTRL+D o chiudi lo stream)
                    if (!scanner.hasNextLine()) {
                        break; 
                    }
                    
                    String command = scanner.nextLine();

                    if ("exit".equalsIgnoreCase(command)) {
                        // Chiudiamo il canale ed usciamo dal loop
                        // Lo scanner verrà chiuso automaticamente dal try
                        clientChannel.close();
                        break;
                    }

                    ClientRequest req = new ClientRequest();

                    if ("login".equalsIgnoreCase(command)) {
                        req.operation = "login";
                        System.out.print("Username: ");
                        req.username = scanner.nextLine(); // Chiediamo l'username vero
                    } 
                    else if ("info".equalsIgnoreCase(command)) {
                        req.operation = "get_game"; 
                    }
                    else if ("submit".equalsIgnoreCase(command)) {
                        req.operation = "propose_solution";
                        // Esempio fisso per ora
                        req.words = Arrays.asList("CANE", "GATTO", "SOLE", "MARE");
                    }
                    else {
                        // Invia comando raw (utile per debug)
                        req.operation = command;
                    }

                    sendRequest(req);
                }
            } // <--- Qui lo Scanner (e System.in) vengono rilasciati automaticamente

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Il resto del codice (sendRequest e ServerListener) rimane identico ---
    
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