package server;

import com.google.gson.Gson;
import server.handlers.AuthHandler;
import server.handlers.GameHandler;
import server.handlers.ResponseUtils;
import server.handlers.StatsHandler;
import server.models.ClientSession;
import utils.ClientRequest;



public class RequestProcessor {
    private static final Gson gson = new Gson();

    public static String process(String jsonText, ClientSession session, ServerMain server) {
        try {
            ClientRequest req = gson.fromJson(jsonText, ClientRequest.class);
            if (req == null || req.operation == null) 
                return ResponseUtils.error("Manca operation", 400);

            switch (req.operation) {
                // --- AUTH HANDLER ---
                case "register":
                    return AuthHandler.handleRegister(req);
                case "updateCredentials":
                    return AuthHandler.handleUpdateCredentials(req);
                case "login":
                    return AuthHandler.handleLogin(req, session);
                case "logout":
                    return AuthHandler.handleLogout(session);

                // --- GAME HANDLER ---
                case "submitProposal":
                    return GameHandler.handleSubmitProposal(req, session);
                case "requestGameInfo":
                    return GameHandler.handleRequestGameInfo(session);

                // --- STATS HANDLER ---
                case "requestGameStats":
                    return StatsHandler.handleRequestGameStats(req, session, server);
                case "requestPlayerStats":
                    return StatsHandler.handleRequestPlayerStats(session);
                case "requestLeaderboard":
                    return StatsHandler.handleRequestLeaderboard(session);

                default:
                    return ResponseUtils.error("Operazione non supportata", 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtils.error("Errore interno: " + e.getMessage(), 500);
        }
    }
    
    public static utils.ServerResponse.GameInfo buildGameInfo(server.models.Game g, ClientSession session) {
        return GameHandler.buildGameInfo(g, session);
    }
}

