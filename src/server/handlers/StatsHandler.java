package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.LiveStats;
import server.models.UserStats;
import utils.ClientRequest;
import utils.ServerResponse;

public class StatsHandler {

    // Gestisce statistiche partita (Globali)
    public static String handleRequestGameStats(ClientRequest.GameInfo req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        Game currentG = GameManager.getInstance().getCurrentGame();
        int gId = (req.gameId != null && req.gameId != 0) ? req.gameId : (currentG != null ? currentG.getGameId() : -1);
        
        if (currentG == null || currentG.getGameId() != gId) {
            return ResponseUtils.error("Statistiche live disponibili solo per la partita corrente", 404);
        }

        ServerResponse.GameStats resp = new ServerResponse.GameStats();
        resp.gameId = gId;
        resp.timeLeft = GameManager.getInstance().getTimeLeft();
        
        LiveStats stats = GameManager.getInstance().calculateStats();        
        resp.playersActive = stats.active;
        resp.playersFinished = stats.finished;
        resp.playersWon = stats.won;
        
        return ResponseUtils.toJson(resp);
    }

    // Gestisce statistiche personali del giocatore
    public static String handleRequestPlayerStats(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        UserStats stats = UserManager.getInstance().getUserStats(session.getUsername());
        
        ServerResponse.PlayerStats resp = new ServerResponse.PlayerStats();
        
        if (stats != null) {
            resp.puzzlesCompleted = stats.puzzlesPlayed;
            resp.currentStreak = stats.currentStreak;
            resp.maxStreak = stats.maxStreak;
            resp.mistakeHistogram = stats.winDistribution; 
            
            if (stats.puzzlesPlayed > 0) {
                resp.winRate = ((float)stats.puzzlesWon / stats.puzzlesPlayed) * 100.0f;
                resp.lossRate = 100.0f - resp.winRate;
            } else {
                resp.winRate = 0.0f;
                resp.lossRate = 0.0f;
            }
            
            if (stats.winDistribution != null && stats.winDistribution.length > 0) {
                resp.perfectPuzzles = stats.winDistribution[0];
            } else {
                resp.perfectPuzzles = 0;
            }
        }
        return ResponseUtils.toJson(resp);
    }

    // Gestisce la classifica
    public static String handleRequestLeaderboard(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        ServerResponse.Leaderboard resp = new ServerResponse.Leaderboard(UserManager.getInstance().getLeaderboard());
        return ResponseUtils.toJson(resp);
    }
}