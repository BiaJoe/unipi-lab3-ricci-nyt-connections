package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.PlayerGameState;
import server.ui.ServerLogger;
import utils.ClientRequest;
import utils.ServerResponse;

public class AuthHandler {

    public static String handleRegister(ClientRequest.Register req, ClientSession session) {
        if (req.name == null || req.psw == null) return ResponseUtils.error("Dati mancanti", 401);
        
        boolean ok = UserManager.getInstance().register(req.name, req.psw);
        if (ok) {
            ServerLogger.info("Nuovo utente registrato: " + req.name);
            
            // 1. Auto Login
            UserManager.getInstance().login(req.name, req.psw);
            
            // 2. Setup Sessione
            session.setUsername(req.name);
            session.setLoggedIn(true);
            session.setUdpPort(0); 

            // 3. Binding Match Corrente (se esiste)
            GameMatch currentMatch = GameManager.getInstance().getCurrentMatch();
            ServerResponse.GameInfoData infoData = null;
            
            if (currentMatch != null) {
                PlayerGameState pState = currentMatch.getOrCreatePlayerState(req.name);
                session.bindState(pState);
                
                // Costruiamo la scheda iniziale
                infoData = InfoHandler.buildGameInfoData(currentMatch, session);
            }
            
            return ResponseUtils.toJson(new ServerResponse.Auth("Registrazione avvenuta. Benvenuto!", infoData));
            
        } else {
            return ResponseUtils.error("Username già in uso", 402);
        }
    }

    public static String handleLogin(ClientRequest.Login req, ClientSession session) {
        if (session.isLoggedIn()) return ResponseUtils.error("Già loggato", 405);
        
        if (UserManager.getInstance().login(req.username, req.psw)) {
            session.setUsername(req.username);
            session.setLoggedIn(true);
            if (req.udpPort > 0) session.setUdpPort(req.udpPort);
            
            ServerLogger.info("Utente loggato: " + req.username);

            GameMatch currentMatch = GameManager.getInstance().getCurrentMatch();
            ServerResponse.GameInfoData infoData = null;
            
            if (currentMatch != null) {
                PlayerGameState pState = currentMatch.getOrCreatePlayerState(req.username);
                session.bindState(pState);
                
                infoData = InfoHandler.buildGameInfoData(currentMatch, session);
            }
            
            return ResponseUtils.toJson(new ServerResponse.Auth("Login effettuato", infoData));
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
    
    public static String handleUpdateCredentials(ClientRequest.UpdateCredentials req) {
        boolean ok = UserManager.getInstance().updateCredentials(req.oldName, req.newName, req.oldPsw, req.newPsw);
        if (ok) return ResponseUtils.success("Credenziali aggiornate");
        else return ResponseUtils.error("Errore: credenziali errate o nuovo username occupato", 403);
    }
}