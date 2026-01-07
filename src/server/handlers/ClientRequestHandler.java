package server.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import server.models.ClientSession;
import utils.ClientRequest;

public class ClientRequestHandler {
    private static final Gson gson = new Gson();

    /**
     * Smista la richiesta al gestore corretto.
     * NOTA: Deve essere STATIC per essere chiamato da PacketHandler senza istanza.
     */
    public static String handleRequest(String jsonInput, ClientSession session) {
        if (jsonInput == null || jsonInput.isEmpty()) {
            return ResponseUtils.error("Richiesta vuota", 400);
        }

        try {
            // 1. Parsing preliminare
            JsonObject root = JsonParser.parseString(jsonInput).getAsJsonObject();
            if (!root.has("operation")) {
                return ResponseUtils.error("Campo 'operation' mancante", 400);
            }

            String op = root.get("operation").getAsString();

            // 2. Dispatching
            switch (op) {
                case "register":
                    ClientRequest.Register reqReg = gson.fromJson(jsonInput, ClientRequest.Register.class);
                    return AuthHandler.handleRegister(reqReg, session);

                case "login":
                    ClientRequest.Login reqLogin = gson.fromJson(jsonInput, ClientRequest.Login.class);
                    return AuthHandler.handleLogin(reqLogin, session);

                case "logout":
                    return AuthHandler.handleLogout(session);

                case "updateCredentials":
                    ClientRequest.UpdateCredentials reqUpd = gson.fromJson(jsonInput, ClientRequest.UpdateCredentials.class);
                    return AuthHandler.handleUpdateCredentials(reqUpd);

                case "submitProposal":
                    ClientRequest.SubmitProposal reqSub = gson.fromJson(jsonInput, ClientRequest.SubmitProposal.class);
                    return GameHandler.handleSubmitProposal(reqSub, session);

                case "requestGameInfo":
                    ClientRequest.GameInfo reqInfo = gson.fromJson(jsonInput, ClientRequest.GameInfo.class);
                    return InfoHandler.handleRequestGameInfo(reqInfo, session);

                case "requestGameStats":
                    ClientRequest.GameInfo reqGStats = gson.fromJson(jsonInput, ClientRequest.GameInfo.class);
                    return StatsHandler.handleRequestGameStats(reqGStats, session);

                case "requestPlayerStats":
                    return StatsHandler.handleRequestPlayerStats(session);

                case "requestLeaderboard":
                    return StatsHandler.handleRequestLeaderboard(session);

                default:
                    return ResponseUtils.error("Operazione sconosciuta: " + op, 400);
            }

        } catch (JsonSyntaxException e) {
            return ResponseUtils.error("JSON malformato", 400);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtils.error("Errore interno server: " + e.getMessage(), 500);
        }
    }
}