package server.handlers;

import server.GameManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.Game;
import server.models.PlayerGameState;
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
        GameMatch current = manager.getCurrentMatch();
        
        // Se req.gameId è 0 o null, prendiamo la partita corrente
        int targetId = (req.gameId == null || req.gameId == 0) ? 
                       (current != null ? current.getGameId() : -1) : 
                       req.gameId;

        // Recuperiamo il match (dal manager che guarda sia current che history)
        GameMatch match = manager.getGameMatchById(targetId);

        if (match == null) {
            return ResponseUtils.error("Partita " + targetId + " non trovata.", 404);
        }

        ServerResponse.GameInfoData resp = buildGameInfoData(match, session);
        return ResponseUtils.toJson(resp);
    }

    // Costruisce la risposta (funziona sia per Auth che per Info)
    public static ServerResponse.GameInfoData buildGameInfoData(GameMatch match, ClientSession session) {
        ServerResponse.GameInfoData resp = new ServerResponse.GameInfoData("OK");
        
        resp.gameId = match.getGameId();
        resp.timeLeft = match.getTimeLeft(); // 0 se è una partita storica
        
        // Recuperiamo lo stato del player dal match
        // Nota: se è una partita vecchia e il player non c'era, pState sarà null
        PlayerGameState pState = match.getPlayerState(session.getUsername());
        
        // Se il player sta giocando ADESSO e non ha ancora lo stato inizializzato nel match, creiamolo
        if (pState == null && match == GameManager.getInstance().getCurrentMatch()) {
            pState = match.getOrCreatePlayerState(session.getUsername());
        }

        if (pState != null) {
            resp.mistakes = pState.getErrors();
            resp.currentScore = pState.getScore();
            resp.isFinished = pState.isFinished() || resp.timeLeft <= 0;
            
            // Mescolamento parole (Lazy Init)
            if (pState.getShuffledWords().isEmpty()) {
                List<String> newShuffle = new ArrayList<>();
                for (Game.Group group : match.getGameData().getGroups()) {
                    newShuffle.addAll(group.getWords());
                }
                Collections.shuffle(newShuffle);
                pState.setShuffledWords(newShuffle);
            }
            resp.words = new ArrayList<>(pState.getShuffledWords());
            
            // Gruppi indovinati (per colorare di verde)
            resp.correctGroups = new ArrayList<>();
            for (Game.Group gr : match.getGameData().getGroups()) {
                if (pState.isThemeGuessed(gr.getTheme())) {
                    resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
                }
            }
        } else {
            // Caso: Partita storica a cui non ho partecipato
            resp.mistakes = 0;
            resp.currentScore = 0;
            resp.isFinished = true;
            resp.message = "Non hai partecipato a questa partita.";
        }

        // Se la partita è finita (per tempo o per il player), mostriamo la soluzione e i risultati degli altri
        if (Boolean.TRUE.equals(resp.isFinished)) {
            resp.solution = buildSolution(match.getGameData());
            
            // Classifica della partita
            resp.playerResults = new ArrayList<>();
            for (Map.Entry<String, PlayerGameState> entry : match.getPlayers().entrySet()) {
                boolean won = entry.getValue().getScore() == 4;
                resp.playerResults.add(new ServerResponse.PlayerResult(
                    entry.getKey(), 
                    entry.getValue().getErrors(), 
                    won
                ));
            }
        }
        
        return resp;
    }

    public static List<ServerResponse.GroupData> buildSolution(Game g) {
        List<ServerResponse.GroupData> solution = new ArrayList<>();
        if (g != null) {
            for (Game.Group gr : g.getGroups()) {
                solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return solution;
    }
}