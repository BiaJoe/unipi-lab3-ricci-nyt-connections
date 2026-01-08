package server.handlers;

import com.google.gson.Gson;
import server.models.Game;
import server.models.GameMatch;
import server.models.PlayerGameState;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResponseUtils {
    private static final Gson gson = new Gson();

    public static String error(String msg, int code) { return gson.toJson(new ServerResponse.Error(msg, code)); }
    public static String success(String msg) { return gson.toJson(new ServerResponse.Generic(msg)); }
    public static String toJson(Object resp) { return gson.toJson(resp); }

    public static ServerResponse.GameInfoData buildGameInfo(GameMatch match, PlayerGameState pState) {
        ServerResponse.GameInfoData resp = new ServerResponse.GameInfoData("OK");
        
        resp.gameId = match.getGameId();
        resp.timeLeft = match.getTimeLeft();

        if (pState != null) {
            resp.mistakes = pState.getErrors();
            resp.currentScore = pState.getScore();
            resp.isFinished = pState.isFinished() || match.getTimeLeft() <= 0;
            
            // LOGICA RIMOSSA: Leggiamo direttamente dallo stato
            resp.isWinner = pState.hasWon(); 
            
            resp.words = pState.getShuffledWords();
            resp.correctGroups = new ArrayList<>();
            for (Game.Group gr : match.getGameData().getGroups()) {
                if (pState.isThemeGuessed(gr.getTheme())) {
                    resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
                }
            }
        } else {
            resp.mistakes = 0;
            resp.currentScore = 0;
            resp.isFinished = true;
            resp.isWinner = false; // Spettatore
            resp.words = new ArrayList<>();
            resp.correctGroups = new ArrayList<>();
            resp.message = "Non hai partecipato a questa partita.";
        }

        if (Boolean.TRUE.equals(resp.isFinished)) {
            resp.solution = buildSolution(match.getGameData());
            resp.playerResults = buildMatchRanking(match);
        }

        return resp;
    }

    public static List<ServerResponse.GroupData> buildSolution(Game game) {
        List<ServerResponse.GroupData> solution = new ArrayList<>();
        if (game != null) {
            for (Game.Group gr : game.getGroups()) {
                solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return solution;
    }

    public static List<ServerResponse.PlayerResult> buildMatchRanking(GameMatch match) {
        List<ServerResponse.PlayerResult> results = new ArrayList<>();
        for (Map.Entry<String, PlayerGameState> entry : match.getPlayers().entrySet()) {
            PlayerGameState ps = entry.getValue();
            
            results.add(new ServerResponse.PlayerResult(
                entry.getKey(), 
                ps.getScore(), 
                ps.hasWon()
            ));
        }
        return results;
    }
}