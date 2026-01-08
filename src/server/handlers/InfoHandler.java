package server.handlers;

import server.GameManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.PlayerGameState;
import utils.ClientRequest;

public class InfoHandler {

    public static String handleRequestGameInfo(ClientRequest.GameInfo req, ClientSession session) {
        if (!session.isLoggedIn())
            return ResponseUtils.error("Non loggato", 401);

        GameManager manager = GameManager.getInstance();
        GameMatch current = manager.getCurrentMatch();

        int targetId;

        // FIX: Rimosso "|| req.gameId == 0". L'ID 0 è valido!
        // Solo null indica "Dammi la corrente".
        if (req.gameId == null) {
            if (current == null) {
                return ResponseUtils.error("Nessuna partita attiva.", 404);
            }
            targetId = current.getGameId();
        } else {
            // L'utente chiede una specifica (anche la 0)
            targetId = req.gameId;
        }

        GameMatch match = manager.getGameMatchById(targetId);

        if (match == null) {
            return ResponseUtils.error("Partita " + targetId + " non trovata.", 404);
        }

        PlayerGameState pState = match.getPlayerState(session.getUsername());

        // Se è la partita CORRENTE e lo stato non esiste, crealo ora
        if (pState == null && match == current) {
            pState = match.getOrCreatePlayerState(session.getUsername());
        }

        return ResponseUtils.toJson(ResponseUtils.buildGameInfo(match, pState));
    }
}