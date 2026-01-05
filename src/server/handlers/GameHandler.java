package server.handlers;

import server.GameManager;
import server.ServerConfig;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.Group;
import server.models.PlayerGameState;
import utils.ClientRequest;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GameHandler {

    public static String handleSubmitProposal(ClientRequest req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        if (req.words == null || req.words.size() != 4) return ResponseUtils.error("Servono 4 parole", 400);
        if (GameManager.getInstance().getTimeLeft() <= 0) return ResponseUtils.error("Tempo scaduto", 408);
        if (session.isGameFinished()) return ResponseUtils.error("Hai già terminato la partita", 409);

        return processProposal(req.words, session, GameManager.getInstance().getCurrentGame());
    }

    public static String handleRequestGameInfo(ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        return getGameInfoResponse(GameManager.getInstance().getCurrentGame(), session);
    }

    private static String processProposal(List<String> userWords, ClientSession session, Game game) {
        if (game == null) return ResponseUtils.error("Nessuna partita in corso", 500);

        // ... Validazioni (uguale a prima) ...
        List<String> normalizedProposal = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        List<String> allGameWords = new ArrayList<>();
        for(Group g : game.getGroups()) allGameWords.addAll(g.getWords());
        
        if (!allGameWords.containsAll(normalizedProposal)) return ResponseUtils.error("Proposta non valida.", 410);
        
        for (String theme : session.getGuessedThemes()) {
            for(Group g : game.getGroups()) {
                if(g.getTheme().equals(theme)) {
                    for(String w : normalizedProposal) {
                        if(g.getWords().contains(w)) return ResponseUtils.error("Parola già usata.", 411);
                    }
                }
            }
        }
        
        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        
        boolean found = false;
        for (Group group : game.getGroups()) {
            if (session.isThemeGuessed(group.getTheme())) continue;
            
            if (group.getWords().containsAll(normalizedProposal) && normalizedProposal.containsAll(group.getWords())) {
                session.addGuessedTheme(group.getTheme());
                resp.isCorrect = true;
                resp.groupTitle = group.getTheme();
                found = true;
                break;
            }
        }

        if (found) {
             resp.message = "Gruppo Trovato!";
             if (session.getScore() == 3) {
                 for (Group g : game.getGroups()) {
                     if (!session.isThemeGuessed(g.getTheme())) {
                         session.addGuessedTheme(g.getTheme()); 
                         break;
                     }
                 }
                 UserManager.getInstance().updateStatsWin(session.getUsername(), session.getErrors());
                 session.setGameFinished(true);
                 resp.message = "VITTORIA!";
                 resp.isFinished = true;
             }
             resp.gameInfo = buildGameInfo(game, session);
        } else {
             session.incrementErrors();
             resp.isCorrect = false;
             resp.message = "Sbagliato.";
             
             if (session.getErrors() >= ServerConfig.MAX_ERRORS) {
                 UserManager.getInstance().updateStatsLoss(session.getUsername());
                 session.setGameFinished(true);
                 resp.message = "HAI PERSO";
                 resp.isFinished = true;
                 resp.gameInfo = buildGameInfo(game, session);
             }
        }
        
        // --- SINCRONIZZAZIONE STATO ---
        // Salviamo subito i progressi nel GameManager (così se slogga sono salvi)
        syncState(session);

        resp.currentMistakes = session.getErrors();
        resp.currentScore = session.getScore();
        return ResponseUtils.toJson(resp);
    }

    // Costruisce la risposta e, se necessario, inizializza lo shuffle nel GameManager
    public static ServerResponse.GameInfo buildGameInfo(Game g, ClientSession session) {
        ServerResponse.GameInfo info = new ServerResponse.GameInfo();
        if (g == null) return info;

        info.gameId = g.getGameId();
        info.mistakes = session.getErrors();
        info.currentScore = session.getScore(); 
        info.timeLeft = GameManager.getInstance().getTimeLeft();

        // 1. Recupera stato persistente dal GameManager
        PlayerGameState pState = GameManager.getInstance().getOrCreatePlayerState(session.getUsername());
        
        // 2. Se non ha uno shuffle (è la prima volta che vede il gioco), crealo
        if (pState.getShuffledWords() == null || pState.getShuffledWords().isEmpty()) {
            List<String> newShuffle = new ArrayList<>();
            for (Group group : g.getGroups()) newShuffle.addAll(group.getWords());
            Collections.shuffle(newShuffle);
            pState.setShuffledWords(newShuffle);
        }

        // 3. Usa lo shuffle persistente per mostrare le parole
        List<String> displayWords = new ArrayList<>();
        for (String word : pState.getShuffledWords()) {
            boolean isGuessed = false;
            for (Group group : g.getGroups()) {
                if (session.isThemeGuessed(group.getTheme()) && group.getWords().contains(word)) {
                    isGuessed = true;
                    break;
                }
            }
            displayWords.add(isGuessed ? "" : word);
        }

        info.words = displayWords;
        return info;
    }

    private static String getGameInfoResponse(Game g, ClientSession session) {
        if (g == null) return ResponseUtils.error("Nessuna partita attiva", 404);

        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.gameId = g.getGameId();
        resp.timeLeft = GameManager.getInstance().getTimeLeft();
        boolean userFinished = session.isGameFinished();
        resp.isFinished = (resp.timeLeft <= 0) || userFinished;
        resp.mistakes = session.getErrors();
        resp.currentScore = session.getScore();
        
        ServerResponse.GameInfo info = buildGameInfo(g, session);
        resp.wordsToGroup = info.words; 
        
        resp.correctGroups = new ArrayList<>();
        for (Group gr : g.getGroups()) {
            if (session.isThemeGuessed(gr.getTheme())) {
                resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        
        if (resp.isFinished) {
            resp.solution = new ArrayList<>();
            for (Group gr : g.getGroups()) {
                resp.solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return ResponseUtils.toJson(resp);
    }
    
    // Aggiorna lo stato nel GameManager con quello attuale della sessione
    private static void syncState(ClientSession session) {
        PlayerGameState pState = GameManager.getInstance().getOrCreatePlayerState(session.getUsername());
        pState.getGuessedThemes().addAll(session.getGuessedThemes());
        // Per gli errori, dobbiamo essere sicuri di non sovrascrivere o raddoppiare in modo strano.
        // Poiché sessione comanda l'interazione, forziamo gli errori del server a pari della sessione.
        // (Un'alternativa sarebbe incrementare anche nel pState ogni volta)
        while(pState.getErrors() < session.getErrors()) {
            pState.incrementErrors();
        }
        pState.setFinished(session.isGameFinished());
    }
}