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

        // 1. Logica di risoluzione ID
        if (req.gameId == null || req.gameId == 0) {
            // L'utente chiede la corrente
            if (current == null) {
                return ResponseUtils.error("Nessuna partita attiva.", 404);
            }
            targetId = current.getGameId();
        } else {
            // L'utente chiede una specifica
            targetId = req.gameId;
        }

        // 2. Cerca la partita (corrente o storico)
        GameMatch match = manager.getGameMatchById(targetId);

        if (match == null) {
            return ResponseUtils.error("Partita " + targetId + " non trovata.", 404);
        }

        // 3. Recupera o Inizializza lo stato del player
        PlayerGameState pState = match.getPlayerState(session.getUsername());

        // Se è la partita CORRENTE e lo stato non esiste, crealo ora (il player entra
        // in gioco)
        if (pState == null && match == current) {
            pState = match.getOrCreatePlayerState(session.getUsername());
        }

        // 4. Costruisci la risposta usando la Utility
        var responseObj = ResponseUtils.buildGameInfo(match, pState);
        return ResponseUtils.toJson(responseObj);
    }
}