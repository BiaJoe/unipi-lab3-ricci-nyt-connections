package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.ServerResponse; // <--- Importante!

public class RequestProcessor {
    private static final Gson gson = new Gson();

    public static String process(String jsonText, ClientSession session, ServerMain server) {
        try {
            JsonObject request = JsonParser.parseString(jsonText).getAsJsonObject();
            
            if (!request.has("operation")) {
                return error("Richiesta malformata: manca 'operation'");
            }

            String op = request.get("operation").getAsString();

            switch (op) {
                case "login":
                    if (!request.has("username")) return error("Manca username");
                    String user = request.get("username").getAsString();
                    // Qui gestiresti password e controlli unicità
                    session.setUsername(user);
                    session.setLoggedIn(true);
                    return success("Login effettuato come " + user, null);

                case "get_game":
                    // Nota: nel client hai usato "requestGameInfo" o "get_game"? 
                    // Assicurati che la stringa qui corrisponda a quella inviata dal Client!
                    // Nel tuo log server ho visto: {"operation":"get_game"} -> OK
                    
                    if (!session.isLoggedIn()) return error("Devi fare login prima");
                    
                    Game g = server.getCurrentGame();
                    if (g == null) return error("Nessuna partita attiva al momento");
                    
                    // ORA LO IMPACCHETTIAMO CORRETTAMENTE
                    return success("Partita corrente recuperata", g);

                case "propose_solution":
                    if (!session.isLoggedIn()) return error("Non loggato");
                    // Logica verifica soluzione...
                    return success("Soluzione ricevuta (logica da implementare)", null);

                default:
                    return error("Operazione sconosciuta: " + op);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("Errore interno nel parsing JSON");
        }
    }

    // --- HELPER PER RISPOSTE UNIFORMI (ServerResponse) ---

    private static String success(String msg, Object data) {
        ServerResponse resp = new ServerResponse(200, msg, data);
        return gson.toJson(resp);
    }

    private static String error(String msg) {
        ServerResponse resp = new ServerResponse(400, msg, null);
        return gson.toJson(resp);
    }
}