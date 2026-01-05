package server.handlers;

import server.GameManager;
import server.ServerMain;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.LiveStats;
import server.models.UserStats;
import utils.ClientRequest;
import utils.ServerResponse;


public class StatsHandler {

    public static String handleRequestGameStats(ClientRequest req, ClientSession session, ServerMain server) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        Game currentG = GameManager.getInstance().getCurrentGame();
        int gId = (req.gameId != null && req.gameId != 0) ? req.gameId : (currentG != null ? currentG.getGameId() : -1);
        
        if (currentG == null || currentG.getGameId() != gId) return ResponseUtils.error("Partita non attiva", 404);

        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.gameId = gId;
        
        LiveStats stats = GameManager.getInstance().calculateStats();        
        resp.playersActive = stats.active;
        resp.playersFinished = stats.finished;
        resp.playersWon = stats.won;
        resp.timeLeft = GameManager.getInstance().getTimeLeft();
        
        return ResponseUtils.toJson(resp);
    }

    public static String handleRequestPlayerStats(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        UserStats stats = UserManager.getInstance().getUserStats(session.getUsername());
        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        
        if (stats != null) {
            resp.puzzlesCompleted = stats.puzzlesPlayed;
            resp.puzzlesWon = stats.puzzlesWon;
            resp.currentStreak = stats.currentStreak;
            resp.maxStreak = stats.maxStreak;
            resp.mistakeHistogram = stats.winDistribution;
            if (stats.puzzlesPlayed > 0) {
                resp.winRate = (float)stats.puzzlesWon / stats.puzzlesPlayed * 100;
                resp.lossRate = 100.0f - resp.winRate;
            }
        }
        return ResponseUtils.toJson(resp);
    }

    public static String handleRequestLeaderboard(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        
        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.rankings = UserManager.getInstance().getLeaderboard();
        
        for (int i = 0; i < resp.rankings.size(); i++) {
            resp.rankings.get(i).position = i + 1;
            if (resp.rankings.get(i).username.equals(session.getUsername())) {
                resp.yourPosition = i + 1;
            }
        }
        return ResponseUtils.toJson(resp);
    }
}