package client;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.Arrays;

public class ClientMain {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8080;
        Gson gson = new Gson();

        // TRY-WITH-RESOURCES: 
        // Tutto ciò che dichiariamo qui dentro verrà chiuso automaticamente alla fine.
        // Abbiamo aggiunto 'scanner' alla lista.
        try (Scanner scanner = new Scanner(System.in);
             Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("--- CLIENT CONNESSO ---");
            System.out.println("Comandi disponibili per test: login, info, submit, exit");

            while (true) {
                System.out.print("\nInserisci comando > ");
                // Verifica se c'è input prima di leggere per evitare errori
                if (!scanner.hasNextLine()) break;
                
                String command = scanner.nextLine();

                if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("Chiusura client...");
                    break;
                }

                // 1. Creazione della richiesta
                ClientRequest req = new ClientRequest();
                
                if ("login".equalsIgnoreCase(command)) {
                    req.operation = "login";
                    req.username = "userTest";
                    req.password = "pass123";
                } 
                else if ("info".equalsIgnoreCase(command)) {
                    req.operation = "requestGameInfo";
                }
                else if ("submit".equalsIgnoreCase(command)) {
                    req.operation = "submitProposal";
                    req.words = Arrays.asList("CANE", "GATTO", "SOLE", "MARE");
                }
                else {
                    req.operation = command;
                }

                // 2. Invio
                String jsonRequest = gson.toJson(req);
                out.println(jsonRequest);

                // 3. Ricezione
                String jsonResponse = in.readLine();
                if (jsonResponse == null) {
                    System.out.println("Il server ha chiuso la connessione.");
                    break;
                }

                // 4. Stampa
                ServerResponse response = gson.fromJson(jsonResponse, ServerResponse.class);
                System.out.println("[RISPOSTA SERVER]: Codice " + response.code + " - " + response.message);
                if (response.data != null) {
                    System.out.println(" -> Dati: " + response.data);
                }
            }

        } catch (IOException e) {
            System.err.println("Errore di connessione: " + e.getMessage());
            System.out.println("Assicurati che ServerMain sia avviato prima del Client!");
        }
    }
}