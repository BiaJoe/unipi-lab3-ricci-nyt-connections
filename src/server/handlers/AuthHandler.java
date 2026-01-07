package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.PlayerGameState;
import server.ui.ServerLogger;
import utils.ClientRequest;
import utils.ServerResponse;

public class AuthHandler {

    // MODIFICATO: Ora richiede ClientSession per fare l'auto-login
    public static String handleRegister(ClientRequest.Register req, ClientSession session) {
        if (req.name == null || req.psw == null) return ResponseUtils.error("Dati mancanti", 401);
        
        boolean ok = UserManager.getInstance().register(req.name, req.psw);
        if (ok) {
            ServerLogger.info("Nuovo utente registrato: " + req.name);
            
            // --- AUTO LOGIN LOGIC ---
            // 1. Attiviamo il login nel UserManager
            UserManager.getInstance().login(req.name, req.psw);
            
            // 2. Setup Sessione
            session.setUsername(req.name);
            session.setLoggedIn(true);
            // Nota: La request di registrazione non ha porta UDP, quindi sarà 0 di default.
            session.setUdpPort(0); 

            // 3. Binding dello stato di gioco
            Game current = GameManager.getInstance().getCurrentGame();
            ServerResponse.GameInfoData infoData = null;
            
            if (current != null) {
                PlayerGameState pState = GameManager.getInstance().getOrCreatePlayerState(req.name);
                session.bindState(pState);
                
                // Creiamo il pacchetto dati di gioco completo
                infoData = InfoHandler.buildGameInfoData(current, session);
            }
            
            // Restituiamo una AUTH response invece di un OK generico
            ServerResponse.Auth resp = new ServerResponse.Auth("Registrazione avvenuta. Benvenuto!", infoData);
            return ResponseUtils.toJson(resp);
            
        } else {
            return ResponseUtils.error("Username già in uso", 402);
        }
    }

    public static String handleUpdateCredentials(ClientRequest.UpdateCredentials req) {
        boolean ok = UserManager.getInstance().updateCredentials(req.oldName, req.newName, req.oldPsw, req.newPsw);
        if (ok) return ResponseUtils.success("Credenziali aggiornate");
        else return ResponseUtils.error("Errore aggiornamento (credenziali errate o username occupato)", 403);
    }

    public static String handleLogin(ClientRequest.Login req, ClientSession session) {
        if (session.isLoggedIn()) return ResponseUtils.error("Già loggato", 405);
        
        if (UserManager.getInstance().login(req.username, req.psw)) {
            // Setup Sessione
            session.setUsername(req.username);
            session.setLoggedIn(true);
            if (req.udpPort > 0) session.setUdpPort(req.udpPort);
            
            ServerLogger.info("Utente loggato: " + req.username);

            // Binding dello stato di gioco
            Game current = GameManager.getInstance().getCurrentGame();
            ServerResponse.GameInfoData infoData = null;
            
            if (current != null) {
                PlayerGameState pState = GameManager.getInstance().getOrCreatePlayerState(req.username);
                session.bindState(pState);
                
                infoData = InfoHandler.buildGameInfoData(current, session);
            }
            
            ServerResponse.Auth resp = new ServerResponse.Auth("Login effettuato", infoData);
            return ResponseUtils.toJson(resp);
        }
        return ResponseUtils.error("Credenziali errate", 401);
    }

    public static String handleLogout(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        ServerLogger.info("Utente logout: " + session.getUsername());
        
        session.setLoggedIn(false);
        session.setUsername(null);
        session.setUdpPort(0);
        session.bindState(null); 
        
        return ResponseUtils.success("Logout effettuato");
    }
}