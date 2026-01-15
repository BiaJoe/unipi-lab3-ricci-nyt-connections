package server.handlers;

import server.ServerConfig;
import server.models.ClientSession;
import server.models.Game;
import server.models.GameMatch;
import server.services.GameManager;
import server.services.UserManager;
import utils.ClientRequest;
import utils.ResponseCodes;
import utils.ServerResponse;
import java.util.ArrayList;
import java.util.List;

/**
 *  Gestisce l'interazione con richieste fatte da qualcuno a conoscenza dei comandi admin
 */
public class AdminHandler {

    private static boolean checkAuth(String inputPsw) {
        return ServerConfig.ADMIN_PASSWORD.equals(inputPsw);
    }

    public static String handleOracle(ClientRequest.Oracle req, ClientSession session) {
        if (!checkAuth(req.password)) return ResponseUtils.error("Password Admin Errata", ResponseCodes.FORBIDDEN);
        
        GameMatch match = GameManager.getInstance().getCurrentMatch();
        if (match == null) return ResponseUtils.error("Nessuna partita attiva", 404);

        // lista dei gruppi
        List<ServerResponse.GroupData> groups = new ArrayList<>();
        for (Game.Group g : match.getGameData().getGroups()) {
            groups.add(new ServerResponse.GroupData(g.getTheme(), g.getWords()));
        }
        
        // risposta
        ServerResponse.AdminInfo resp = new ServerResponse.AdminInfo();
        resp.oracleData = groups; // Assegniamo la lista al campo specifico
        resp.message = "Soluzione  Oracle";
        
        return ResponseUtils.toJson(resp);
    }

    public static String handleGod(ClientRequest.God req) {
        if (!checkAuth(req.password)) return ResponseUtils.error("Password Admin Errata", ResponseCodes.FORBIDDEN);
        
        List<ServerResponse.UserAccountInfo> users = UserManager.getInstance().getUserListDebug();
        return ResponseUtils.toJson(new ServerResponse.AdminInfo(users));
    }
}