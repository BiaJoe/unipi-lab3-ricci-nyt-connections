package server.handlers;

import server.GameManager;
import server.ServerConfig;
import server.UserManager;
import server.models.ClientSession;
import server.models.Game;
import server.models.Group;
import utils.ClientRequest;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameHandler {

    public static String handleSubmitProposal(ClientRequest.SubmitProposal req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        if (req.words == null || req.words.size() != 4) return ResponseUtils.error("Servono 4 parole", 400);
        if (GameManager.getInstance().getTimeLeft() <= 0) return ResponseUtils.error("Tempo scaduto", 408);
        if (session.isGameFinished()) return ResponseUtils.error("Hai già terminato la partita", 409);

        return processProposal(req.words, session, GameManager.getInstance().getCurrentGame());
    }

    private static String processProposal(List<String> userWords, ClientSession session, Game game) {
        if (game == null) return ResponseUtils.error("Nessuna partita in corso", 500);

        List<String> normalizedProposal = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        
        // 1. Validazione esistenza parole
        List<String> allGameWords = new ArrayList<>();
        for(Group g : game.getGroups()) allGameWords.addAll(g.getWords());
        if (!allGameWords.containsAll(normalizedProposal)) return ResponseUtils.error("Parole non valide per questa partita.", 410);
        
        // 2. Validazione parole già indovinate
        for (String theme : session.getGuessedThemes()) {
            for (Group g : game.getGroups()) {
                if (g.getTheme().equals(theme)) {
                    for (String w : normalizedProposal) {
                        if (g.getWords().contains(w)) {
                            return ResponseUtils.error("La parola '" + w + "' appartiene già al gruppo: " + theme, 411);
                        }
                    }
                }
            }
        }
        
        // 3. Verifica Correttezza Gruppo
        boolean found = false;
        Group foundGroup = null;

        for (Group group : game.getGroups()) {
            if (session.isThemeGuessed(group.getTheme())) continue;
            // Controlla se le parole coincidono (senza ordine)
            if (group.getWords().containsAll(normalizedProposal) && normalizedProposal.containsAll(group.getWords())) {
                found = true;
                foundGroup = group;
                break;
            }
        }

        boolean isCorrect = false;
        String groupTitle = null;
        String message = "";
        boolean isFinished = false;

        if (found) {
             session.addGuessedTheme(foundGroup.getTheme());
             isCorrect = true;
             groupTitle = foundGroup.getTheme();
             message = "Gruppo Trovato!";
             
             // Vittoria (3 gruppi trovati -> il 4° è automatico)
             if (session.getScore() == 3) {
                 for (Group g : game.getGroups()) {
                     if (!session.isThemeGuessed(g.getTheme())) {
                         session.addGuessedTheme(g.getTheme()); 
                         break;
                     }
                 }
                 UserManager.getInstance().updateStatsWin(session.getUsername(), session.getErrors());
                 session.setGameFinished(true);
                 message = "VITTORIA!";
                 isFinished = true;
             }
        } else {
             session.incrementErrors();
             isCorrect = false;
             message = "Sbagliato.";
             
             // Sconfitta (Troppi errori)
             if (session.getErrors() >= ServerConfig.MAX_ERRORS) {
                 UserManager.getInstance().updateStatsLoss(session.getUsername());
                 session.setGameFinished(true);
                 message = "HAI PERSO (Troppi errori)";
                 isFinished = true;
             }
        }
        
        // Costruzione Risposta Proposal
        ServerResponse.Proposal resp = new ServerResponse.Proposal(isCorrect, groupTitle, session.getScore());
        resp.message = message;
        
        if (isFinished) {
            resp.isFinished = true;
            resp.solution = InfoHandler.buildSolution(game);
        }

        return ResponseUtils.toJson(resp);
    }
}