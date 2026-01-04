package server;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static void main(String[] args) {
        int port = 8080;
        // Gson è lo strumento che trasforma Oggetti <-> Testo JSON
        Gson gson = new Gson(); 

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("SERVER: In ascolto sulla porta " + port + "...");

            // Loop infinito: il server non muore mai
            while (true) {
                // 1. Il server si blocca qui finché un client non bussa
                Socket clientSocket = serverSocket.accept();
                System.out.println("SERVER: Nuovo client connesso!");

                // Prepariamo i tubi per parlare (Input e Output)
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // 2. Leggiamo la riga inviata dal client (che sarà un JSON)
                String jsonRicevuto = in.readLine();
                
                if (jsonRicevuto != null) {
                    System.out.println("SERVER [RICEVUTO RAW]: " + jsonRicevuto);

                    // 3. Trasformiamo il testo JSON in oggetto Java (Deserializzazione)
                    try {
                        ClientRequest richiesta = gson.fromJson(jsonRicevuto, ClientRequest.class);
                        
                        // DEBUG: Vediamo se ha capito i campi
                        System.out.println("SERVER [INTERPRETATO]: Operazione = " + richiesta.operation);

                        // 4. Prepariamo la risposta (switch case semplice)
                        ServerResponse risposta;
                        
                        if ("login".equals(richiesta.operation)) {
                            risposta = new ServerResponse(200, "Ciao " + richiesta.username + ", login OK!", null);
                        } else {
                            risposta = new ServerResponse(400, "Operazione non gestita", null);
                        }

                        // 5. Trasformiamo la risposta Java in testo JSON (Serializzazione)
                        String jsonRisposta = gson.toJson(risposta);
                        System.out.println("SERVER [INVIATO]: " + jsonRisposta);
                        
                        // Spediamo al client
                        out.println(jsonRisposta);

                    } catch (Exception e) {
                        System.out.println("SERVER: Errore nel leggere il JSON: " + e.getMessage());
                    }
                }
                
                // Chiudiamo il client (per ora facciamo una connessione "usa e getta" per semplicità)
                clientSocket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}