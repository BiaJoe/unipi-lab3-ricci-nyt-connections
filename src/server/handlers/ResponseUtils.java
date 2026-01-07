package server.handlers;

import com.google.gson.Gson;
import server.ServerConfig;
import server.models.Game;
import server.models.GameMatch;
import server.models.PlayerGameState;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResponseUtils {
    private static final Gson gson = new Gson();

    // --- METODI BASE ---

    public static String error(String msg, int code) {
        return gson.toJson(new ServerResponse.Error(msg, code));
    }

    public static String success(String msg) {
        return gson.toJson(new ServerResponse.Generic(msg));
    }

    public static String toJson(Object resp) {
        return gson.toJson(resp);
    }

    // --- FACTORY METHODS (Costruzione Oggetti Complessi) ---

    /**
     * Costruisce l'oggetto completo per inviare lo stato della partita al client.
     * Usato sia da InfoHandler (/i) che all'inizio/fine partita.
     */
    public static ServerResponse.GameInfoData buildGameInfo(GameMatch match, PlayerGameState pState) {
        ServerResponse.GameInfoData resp = new ServerResponse.GameInfoData("OK");
        
        resp.gameId = match.getGameId();
        resp.timeLeft = match.getTimeLeft();

        if (pState != null) {
            // Utente partecipante
            resp.mistakes = pState.getErrors();
            resp.currentScore = pState.getScore(); // Usa il calcolo centralizzato (+6/-4)
            resp.isFinished = pState.isFinished() || match.getTimeLeft() <= 0;
            
            // Usa le parole mescolate salvate nello stato del giocatore
            resp.words = pState.getShuffledWords();
            
            // 2. Gruppi Indovinati (per colorarli lato client)
            resp.correctGroups = new ArrayList<>();
            for (Game.Group gr : match.getGameData().getGroups()) {
                if (pState.isThemeGuessed(gr.getTheme())) {
                    resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
                }
            }
        } else {
            // Utente spettatore / storico a cui non ha partecipato
            resp.mistakes = 0;
            resp.currentScore = 0;
            resp.isFinished = true;
            resp.words = new ArrayList<>();
            resp.correctGroups = new ArrayList<>();
            resp.message = "Non hai partecipato a questa partita.";
        }

        // Se la partita è finita (per il player o per tempo), alleghiamo soluzione e classifica
        if (Boolean.TRUE.equals(resp.isFinished)) {
            resp.solution = buildSolution(match.getGameData());
            resp.playerResults = buildMatchRanking(match);
        }

        return resp;
    }

    /**
     * Crea la lista della soluzione (Gruppi + Parole)
     */
    public static List<ServerResponse.GroupData> buildSolution(Game game) {
        List<ServerResponse.GroupData> solution = new ArrayList<>();
        if (game != null) {
            for (Game.Group gr : game.getGroups()) {
                solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return solution;
    }

    /**
     * Crea la classifica della singola partita (chi ha vinto, errori, punteggio)
     */
    public static List<ServerResponse.PlayerResult> buildMatchRanking(GameMatch match) {
        List<ServerResponse.PlayerResult> results = new ArrayList<>();
        for (Map.Entry<String, PlayerGameState> entry : match.getPlayers().entrySet()) {
            PlayerGameState ps = entry.getValue();
            
            // Logica vittoria: 4 gruppi trovati E errori sotto il limite
            boolean won = ps.getGroupsFoundCount() == 4 && ps.getErrors() < ServerConfig.MAX_ERRORS;
            
            // Creiamo il risultato usando lo SCORE corretto
            results.add(new ServerResponse.PlayerResult(
                entry.getKey(), 
                ps.getScore(), // Passiamo il punteggio calcolato (es. 16, 24)
                won
            ));
        }
        return results;
    }
}