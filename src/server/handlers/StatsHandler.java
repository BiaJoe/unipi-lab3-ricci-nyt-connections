package server.handlers;

import server.models.ClientSession;
import server.models.GameMatch;
import server.models.User;
import server.services.GameManager;
import server.services.UserManager;
import utils.ClientRequest;
import utils.ServerResponse;
import utils.ResponseCodes;

public class StatsHandler {

    // Gestione Statistiche Partita (/gs)
    public static String handleRequestGameStats(ClientRequest.RequestGameStats req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        
        // FIX: Rimosso "|| req.gameId == 0".
        // Se req.gameId è null, prendiamo la corrente. Se è 0, prendiamo la 0.
        GameMatch match = (req.gameId == null) 
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
        resp.averageScore = snap.averageScore;
        
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

    public static String handleRequestLeaderboard(ClientRequest.Leaderboard req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        
        var ranking = UserManager.getInstance().getLeaderboard(req.topPlayers, req.playerName);
        
        if (ranking.isEmpty() && req.playerName != null) {
            return ResponseUtils.error("Giocatore non trovato in classifica", ResponseCodes.NOT_FOUND);
        }

        ServerResponse.Leaderboard resp = new ServerResponse.Leaderboard(ranking);
        return ResponseUtils.toJson(resp);
    }
}