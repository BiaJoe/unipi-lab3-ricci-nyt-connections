package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.User;
import utils.ClientRequest;
import utils.ServerResponse;
import utils.ResponseCodes;

public class StatsHandler {

    // Gestione Statistiche Partita (/gs)
    public static String handleRequestGameStats(ClientRequest.RequestGameStats req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        
        // 0 o null significa "partita corrente"
        GameMatch match = (req.gameId == null || req.gameId == 0) 
            ? GameManager.getInstance().getCurrentMatch()
            : GameManager.getInstance().getGameMatchById(req.gameId);

        if (match == null) return ResponseUtils.error("Partita non trovata", ResponseCodes.NOT_FOUND);

        ServerResponse.GameStats resp = new ServerResponse.GameStats();
        resp.gameId = match.getGameId();
        resp.timeLeft = match.getTimeLeft();
        
        GameMatch.StatsSnapshot snap = match.getStatsSnapshot();
        resp.playersActive = snap.active;
        resp.playersFinished = snap.finished;
        resp.playersWon = snap.won;
        
        // Calcolo media (opzionale, se richiesto dai requisiti avanzati)
        // Per ora lasciamo null o calcoliamo se abbiamo i dati storici completi nel match
        
        return ResponseUtils.toJson(resp);
    }

    // Gestione Statistiche Personali (/me)
    public static String handleRequestPlayerStats(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        
        User user = UserManager.getInstance().getUser(session.getUsername());
        ServerResponse.PlayerStats resp = new ServerResponse.PlayerStats();
        
        if (user != null) {
            resp.puzzlesCompleted = user.getPuzzlesPlayed();
            resp.currentStreak = user.getCurrentStreak();
            resp.maxStreak = user.getMaxStreak();
            resp.mistakeHistogram = user.getWinDistribution(); 
            
            if (user.getPuzzlesPlayed() > 0) {
                resp.winRate = ((float)user.getPuzzlesWon() / user.getPuzzlesPlayed()) * 100.0f;
                resp.lossRate = 100.0f - resp.winRate;
            }
            
            if (resp.mistakeHistogram != null && resp.mistakeHistogram.length > 0) {
                resp.perfectPuzzles = resp.mistakeHistogram[0];
            }
        }
        return ResponseUtils.toJson(resp);
    }

    // Gestione Classifica (/rk)
    public static String handleRequestLeaderboard(ClientRequest.Leaderboard req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        
        // Passiamo i parametri di filtro al manager
        var ranking = UserManager.getInstance().getLeaderboard(req.topPlayers, req.playerName);
        
        if (ranking.isEmpty() && req.playerName != null) {
            return ResponseUtils.error("Giocatore non trovato in classifica", ResponseCodes.NOT_FOUND);
        }

        ServerResponse.Leaderboard resp = new ServerResponse.Leaderboard(ranking);
        return ResponseUtils.toJson(resp);
    }
}