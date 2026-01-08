package server.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import server.models.ClientSession;
import utils.ClientRequest;
import utils.ResponseCodes;

/**
 * smista le varie richieste agli handler
 */
public class ClientRequestHandler {
    private static final Gson gson = new Gson();

    public static String handleRequest(String jsonInput, ClientSession session) {
        if (jsonInput == null || jsonInput.isEmpty()) return ResponseUtils.error("Richiesta vuota", ResponseCodes.BAD_REQUEST);

        try {
            JsonObject root = JsonParser.parseString(jsonInput).getAsJsonObject();
            if (!root.has("operation")) return ResponseUtils.error("Campo 'operation' mancante", ResponseCodes.BAD_REQUEST);

            String op = root.get("operation").getAsString();

            switch (op) {
                case "register":
                    return AuthHandler.handleRegister(gson.fromJson(jsonInput, ClientRequest.Register.class), session);
                case "login":
                    return AuthHandler.handleLogin(gson.fromJson(jsonInput, ClientRequest.Login.class), session);
                case "logout":
                    return AuthHandler.handleLogout(session);
                case "updateCredentials":
                    return AuthHandler.handleUpdateCredentials(gson.fromJson(jsonInput, ClientRequest.UpdateCredentials.class));
                case "submitProposal":
                    return GameHandler.handleSubmitProposal(gson.fromJson(jsonInput, ClientRequest.SubmitProposal.class), session);
                case "requestGameInfo":
                    return InfoHandler.handleRequestGameInfo(gson.fromJson(jsonInput, ClientRequest.GameInfo.class), session);
                case "requestGameStats":
                    return StatsHandler.handleRequestGameStats(gson.fromJson(jsonInput, ClientRequest.RequestGameStats.class), session);
                case "requestPlayerStats":
                    return StatsHandler.handleRequestPlayerStats(session);
                case "requestLeaderboard":
                    return StatsHandler.handleRequestLeaderboard(gson.fromJson(jsonInput, ClientRequest.Leaderboard.class), session);
                
                // ADMIN
                case "oracle":
                    return AdminHandler.handleOracle(gson.fromJson(jsonInput, ClientRequest.Oracle.class), session);
                case "god":
                    return AdminHandler.handleGod(gson.fromJson(jsonInput, ClientRequest.God.class));

                default:
                    return ResponseUtils.error("Operazione sconosciuta: " + op, ResponseCodes.BAD_REQUEST);
            }
        } catch (JsonSyntaxException e) {
            return ResponseUtils.error("JSON malformato", ResponseCodes.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtils.error("Errore interno server: " + e.getMessage(), ResponseCodes.INTERNAL_SERVER_ERROR);
        }
    }
}