package server.handlers;

import server.GameManager;
import server.UserManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.User;
import utils.ClientRequest;
import utils.ServerResponse;

public class StatsHandler {

    public static String handleRequestGameStats(ClientRequest.GameInfo req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        GameMatch match = (req.gameId == null || req.gameId == 0) 
            ? GameManager.getInstance().getCurrentMatch()
            : GameManager.getInstance().getGameMatchById(req.gameId);

        if (match == null) return ResponseUtils.error("Partita non trovata", 404);

        ServerResponse.GameStats resp = new ServerResponse.GameStats();
        resp.gameId = match.getGameId();
        resp.timeLeft = match.getTimeLeft();
        
        GameMatch.StatsSnapshot snap = match.getStatsSnapshot();
        resp.playersActive = snap.active;
        resp.playersFinished = snap.finished;
        resp.playersWon = snap.won;
        
        return ResponseUtils.toJson(resp);
    }

    public static String handleRequestPlayerStats(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
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

    public static String handleRequestLeaderboard(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        ServerResponse.Leaderboard resp = new ServerResponse.Leaderboard(UserManager.getInstance().getLeaderboard());
        return ResponseUtils.toJson(resp);
    }
}