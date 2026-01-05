package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.PlayerGameState;
import utils.ClientRequest;
import utils.ServerResponse;

public class AuthHandler {

    public static String handleRegister(ClientRequest req) {
        if (req.name == null || req.psw == null) return ResponseUtils.error("Dati mancanti", 401);
        boolean ok = UserManager.getInstance().register(req.name, req.psw);
        if (ok) return ResponseUtils.success("Registrazione avvenuta");
        else return ResponseUtils.error("Username già in uso", 402);
    }

    public static String handleUpdateCredentials(ClientRequest req) {
        boolean ok = UserManager.getInstance().updateCredentials(req.oldName, req.newName, req.oldPsw, req.newPsw);
        if (ok) return ResponseUtils.success("Credenziali aggiornate");
        else return ResponseUtils.error("Errore aggiornamento", 403);
    }

    public static String handleLogin(ClientRequest req, ClientSession session) {
        if (session.isLoggedIn()) return ResponseUtils.error("Già loggato", 405);
        
        if (UserManager.getInstance().login(req.username, req.psw)) {
            session.setUsername(req.username);
            session.setLoggedIn(true);
            
            Game current = GameManager.getInstance().getCurrentGame();
            if (current != null) {
                // IL SERVER RICORDA IL GIOCO DELL'UTENTE
                PlayerGameState savedState = GameManager.getInstance().getOrCreatePlayerState(req.username);
                // Carichiamo lo stato nella sessione attiva del socket
                session.restoreFromState(savedState);
            }
            
            ServerResponse resp = new ServerResponse();
            resp.status = "OK";
            resp.message = "Login effettuato";
            resp.gameInfo = GameHandler.buildGameInfo(current, session);
            return ResponseUtils.toJson(resp);
        }
        return ResponseUtils.error("Credenziali errate", 401);
    }

    public static String handleLogout(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        session.setLoggedIn(false);
        session.setUsername(null);
        session.resetGameStatus(); 
        return ResponseUtils.success("Logout effettuato");
    }
}