package server;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private Gson gson;
    private BufferedReader in;
    private PrintWriter out;
    private String currentUser = null; // Per sapere chi è loggato su questo socket

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Ricevuto dal client: " + line);
                
                // 1. Parsing della richiesta
                ClientRequest req = null;
                try {
                    req = gson.fromJson(line, ClientRequest.class);
                } catch (Exception e) {
                    sendResponse(400, "JSON Malformato", null);
                    continue;
                }

                if (req == null || req.operation == null) {
                    sendResponse(400, "Operazione mancante", null);
                    continue;
                }

                // 2. Routing dell'operazione (Switch-Case espandibile)
                handleRequest(req);
            }
        } catch (IOException e) {
            System.out.println("Client disconnesso.");
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void handleRequest(ClientRequest req) {
        switch (req.operation) {
            case "login":
                // TODO: Controllare username/password veri
                this.currentUser = req.username;
                sendResponse(200, "Login effettuato con successo", null);
                break;

            case "requestGameInfo":
                // TODO: Recuperare info vere dal GameManager
                // Simuliamo una risposta con dati dummy per il prototipo
                sendResponse(200, "Info partita recuperate", "Parole: [CANE, GATTO, SOLE...]"); 
                break;

            case "submitProposal":
                if (req.words == null || req.words.size() != 4) {
                    sendResponse(400, "Devi inviare esattamente 4 parole", null);
                    return;
                }
                // TODO: Controllare se le parole sono un gruppo valido
                System.out.println("Utente " + currentUser + " propone: " + req.words);
                sendResponse(200, "Tentativo ricevuto (Simulazione)", "RISULTATO: SBAGLIATO");
                break;

            default:
                sendResponse(404, "Operazione sconosciuta: " + req.operation, null);
        }
    }

    private void sendResponse(int code, String message, Object data) {
        ServerResponse res = new ServerResponse(code, message, data);
        String jsonStr = gson.toJson(res);
        out.println(jsonStr);
    }
}