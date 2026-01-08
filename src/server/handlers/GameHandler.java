package server.handlers;

import server.GameManager;
import server.ServerConfig;
import server.UserManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.Game;
import server.models.PlayerGameState;
import utils.ClientRequest;
import utils.ServerResponse;
import utils.ResponseCodes; 

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameHandler {

    public static String handleSubmitProposal(ClientRequest.SubmitProposal req, ClientSession session) {
        if (!session.isLoggedIn()) 
            return ResponseUtils.error("Non loggato", ResponseCodes.UNAUTHORIZED);
        if (req.words == null || req.words.size() != 4) 
            return ResponseUtils.error("Servono 4 parole", ResponseCodes.BAD_REQUEST);

        GameMatch match = GameManager.getInstance().getCurrentMatch();
        if (match == null) return ResponseUtils.error("Nessuna partita attiva", ResponseCodes.INTERNAL_SERVER_ERROR);
        if (match.getTimeLeft() <= 0) return ResponseUtils.error("Tempo scaduto", ResponseCodes.TIMEOUT);

        PlayerGameState state = match.getOrCreatePlayerState(session.getUsername());
        if (state.isFinished()) return ResponseUtils.error("Hai già terminato la partita", ResponseCodes.GAME_FINISHED);

        return processProposal(req.words, session, match, state);
    }

    private static String processProposal(List<String> userWords, ClientSession session, GameMatch match, PlayerGameState state) {
        List<String> normalized = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        Game.Group[] groups = match.getGameData().getGroups().toArray(new Game.Group[0]); 
        
        // 1. Validazione esistenza parole
        List<String> allGameWords = new ArrayList<>();
        for(Game.Group g : groups) allGameWords.addAll(g.getWords());
        if (!allGameWords.containsAll(normalized)) return ResponseUtils.error("Parole non valide.", ResponseCodes.INVALID_WORDS);
        
        // 2. Validazione duplicati
        for (String theme : state.getGuessedThemes()) {
            for (Game.Group g : groups) {
                if (g.getTheme().equals(theme)) {
                    for (String w : normalized) {
                        if (g.getWords().contains(w)) return ResponseUtils.error("Parola già usata: " + theme, ResponseCodes.DUPLICATE_GUESS);
                    }
                }
            }
        }
        
        // 3. Logica 
        boolean found = false;
        Game.Group foundGroup = null;

        for (Game.Group group : groups) {
            if (state.isThemeGuessed(group.getTheme())) continue;
            if (group.getWords().containsAll(normalized) && normalized.containsAll(group.getWords())) {
                found = true;
                foundGroup = group;
                break;
            }
        }

        boolean isCorrect = false;
        String groupTitle = null;
        String message;
        boolean isFinished = false;

        if (found) {
             state.addGuessedTheme(foundGroup.getTheme());
             isCorrect = true;
             groupTitle = foundGroup.getTheme();
             message = "Gruppo Trovato!";
             
             // --- LOGICA VITTORIA CENTRALIZZATA ---
             // Qui decidiamo che 3 gruppi bastano per vincere.
             if (state.getGroupsFoundCount() == 3) {
                 finishGame(session, state, true); // <--- Setta won=true
                 message = "VITTORIA!";
                 isFinished = true;
             }
        } else {
             state.incrementErrors();
             isCorrect = false;
             message = "Sbagliato.";
             
             // --- LOGICA SCONFITTA CENTRALIZZATA ---
             if (state.getErrors() > ServerConfig.MAX_ERRORS) {
                 finishGame(session, state, false); // <--- Setta won=false
                 message = "HAI PERSO (Troppi errori)";
                 isFinished = true;
             }
        }
        
        ServerResponse.Proposal resp = new ServerResponse.Proposal(isCorrect, groupTitle, state.getScore());
        resp.message = message;
        if (isFinished) {
            resp.isFinished = true;
            resp.solution = ResponseUtils.buildSolution(match.getGameData());
        }

        return ResponseUtils.toJson(resp);
    }
    
    private static void finishGame(ClientSession session, PlayerGameState state, boolean won) {
        state.setFinished(true);
        state.setWon(won); 
        UserManager.getInstance().updateGameResult(session.getUsername(), state.getScore(), state.getErrors(), won);
    }
}