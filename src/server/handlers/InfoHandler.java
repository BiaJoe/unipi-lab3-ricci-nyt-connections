package server.handlers;

import server.GameManager;
import server.models.*;
import utils.ClientRequest;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InfoHandler {

    public static String handleRequestGameInfo(ClientRequest.GameInfo req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);

        GameManager manager = GameManager.getInstance();
        Game currentGame = manager.getCurrentGame();
        
        int requestedId = (req.gameId == null || req.gameId == 0) ? 
                          (currentGame != null ? currentGame.getGameId() : -1) : 
                          req.gameId;

        // CASO A: Partita Corrente
        if (currentGame != null && currentGame.getGameId() == requestedId) {
            ServerResponse.GameInfoData resp = buildGameInfoData(currentGame, session);
            return ResponseUtils.toJson(resp);
        }
        
        // CASO B: Partita Storico
        PlayedGame archived = manager.getArchivedGame(requestedId);
        if (archived != null) {
            return getArchivedGameInfo(archived, session);
        }

        return ResponseUtils.error("Partita " + requestedId + " non trovata.", 404);
    }

    // Costruisce la risposta completa (usata anche da AuthHandler)
    public static ServerResponse.GameInfoData buildGameInfoData(Game g, ClientSession session) {
        ServerResponse.GameInfoData resp = new ServerResponse.GameInfoData("OK");
        
        // Popolamento campi piatti (Flat fields)
        resp.gameId = g.getGameId();
        resp.timeLeft = GameManager.getInstance().getTimeLeft();
        resp.mistakes = session.getErrors();
        resp.currentScore = session.getScore();
        resp.isFinished = session.isGameFinished() || resp.timeLeft <= 0;
        
        // Logica Mescolamento Parole (direttamente qui, niente DTO intermedio)
        PlayerGameState pState = GameManager.getInstance().getOrCreatePlayerState(session.getUsername());
        
        if (pState.getShuffledWords() == null || pState.getShuffledWords().isEmpty()) {
            List<String> newShuffle = new ArrayList<>();
            for (Group group : g.getGroups()) newShuffle.addAll(group.getWords());
            Collections.shuffle(newShuffle);
            pState.setShuffledWords(newShuffle);
        }

        resp.words = resp.words = new ArrayList<>(pState.getShuffledWords());

        // Popola i gruppi indovinati
        resp.correctGroups = new ArrayList<>();
        for (Group gr : g.getGroups()) {
            if (session.isThemeGuessed(gr.getTheme())) {
                resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        
        // Se finita, aggiunge soluzione
        if (Boolean.TRUE.equals(resp.isFinished)) {
            resp.solution = buildSolution(g);
        }
        
        return resp;
    }

    private static String getArchivedGameInfo(PlayedGame pg, ClientSession session) {
        PlayerGameState myOldState = pg.getPlayerState(session.getUsername());
        int gId = pg.getGameData().getGameId();
        
        ServerResponse.GameInfoData resp = new ServerResponse.GameInfoData("Storico Partita " + gId);
        resp.gameId = gId;
        resp.timeLeft = 0; 
        resp.isFinished = true;
        resp.solution = buildSolution(pg.getGameData());

        if (myOldState != null) {
            resp.mistakes = myOldState.getErrors();
            resp.currentScore = myOldState.getScore();
        } else {
            resp.mistakes = 0;
            resp.currentScore = 0;
            resp.message = "Non hai partecipato a questa partita.";
        }
        
        // Lista risultati partecipanti
        resp.playerResults = new ArrayList<>();
        Map<String, PlayerGameState> allPlayers = pg.getPlayerResults();
        
        if (allPlayers != null) {
            for (Map.Entry<String, PlayerGameState> entry : allPlayers.entrySet()) {
                boolean won = entry.getValue().getScore() == 4; 
                resp.playerResults.add(new ServerResponse.PlayerResult(entry.getKey(), entry.getValue().getErrors(), won));
            }
        }
        
        return ResponseUtils.toJson(resp);
    }

    public static List<ServerResponse.GroupData> buildSolution(Game g) {
        List<ServerResponse.GroupData> solution = new ArrayList<>();
        if (g != null) {
            for (Group gr : g.getGroups()) {
                solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return solution;
    }
}