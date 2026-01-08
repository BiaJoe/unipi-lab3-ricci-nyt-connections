package server.handlers;

import server.models.ClientSession;
import server.models.GameMatch;
import server.models.PlayerGameState;
import server.services.GameManager;
import server.services.UserManager;
import server.ui.ServerLogger;
import utils.ClientRequest;
import utils.ServerResponse;

/**
 * Gestisce login e registrazione
 */
public class AuthHandler {

    public static String handleRegister(ClientRequest.Register req, ClientSession session) {
        if (req.name == null || req.psw == null) {
            return ResponseUtils.error("Dati mancanti", 400);
        }
        
        boolean registered = UserManager.getInstance().register(req.name, req.psw);
        if (registered) {
            ServerLogger.info("Nuovo utente registrato: " + req.name);
            
            // Auto Login immediato dopo la registrazione
            UserManager.getInstance().login(req.name, req.psw);
            
            return completeLoginProcess(session, req.name, 0, "Registrazione avvenuta. Benvenuto!");
        } else {
            return ResponseUtils.error("Username già in uso", 409);
        }
    }

    public static String handleLogin(ClientRequest.Login req, ClientSession session) {
        if (session.isLoggedIn()) {
            return ResponseUtils.error("Già loggato", 405);
        }
        
        if (UserManager.getInstance().login(req.username, req.psw)) {
            ServerLogger.info("Utente loggato: " + req.username);
            return completeLoginProcess(session, req.username, req.udpPort, "Login effettuato");
        }
        
        return ResponseUtils.error("Credenziali errate", 401);
    }

    public static String handleLogout(ClientSession session) {
        if (!session.isLoggedIn()) {
            return ResponseUtils.error("Non eri loggato", 401);
        }
        
        ServerLogger.info("Utente logout: " + session.getUsername());
        
        session.setLoggedIn(false);
        session.setUsername(null);
        session.setUdpPort(0);
        
        return ResponseUtils.success("Logout effettuato");
    }
    
    public static String handleUpdateCredentials(ClientRequest.UpdateCredentials req) {
        boolean ok = UserManager.getInstance().updateCredentials(req.oldName, req.newName, req.oldPsw, req.newPsw);
        
        if (ok) {
            return ResponseUtils.success("Credenziali aggiornate");
        } else {
            return ResponseUtils.error("Errore: credenziali errate o nuovo username occupato", 403);
        }
    }

    // --- HELPER PRIVATO PER NON DUPLICARE CODICE ---
    
    private static String completeLoginProcess(ClientSession session, String username, int udpPort, String successMessage) {
        // 1. Setup Sessione
        session.setUsername(username);
        session.setLoggedIn(true);
        if (udpPort > 0) session.setUdpPort(udpPort);

        // 2. Controllo Partita Attiva
        GameMatch currentMatch = GameManager.getInstance().getCurrentMatch();
        ServerResponse.GameInfoData infoData = null;
        
        if (currentMatch != null) {
            // Se c'è una partita, recuperiamo/creiamo lo stato del giocatore
            PlayerGameState pState = currentMatch.getOrCreatePlayerState(username);
            
            // Costruiamo l'oggetto GameInfoData usando la Factory in ResponseUtils
            infoData = ResponseUtils.buildGameInfo(currentMatch, pState);
        }
        
        // 3. Risposta Finale (Auth + eventuale GameInfo)
        return ResponseUtils.toJson(new ServerResponse.Auth(successMessage, infoData));
    }
}