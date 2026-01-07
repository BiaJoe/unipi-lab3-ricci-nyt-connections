package server.handlers;

import server.GameManager;
import server.ServerConfig;
import server.UserManager;
import server.models.ClientSession;
import server.models.GameMatch;
import server.models.Game;
import utils.ClientRequest;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameHandler {

    public static String handleSubmitProposal(ClientRequest.SubmitProposal req, ClientSession session) {
        if (!session.isLoggedIn()) return ResponseUtils.error("Non loggato", 401);
        if (req.words == null || req.words.size() != 4) return ResponseUtils.error("Servono 4 parole", 400);
        
        GameMatch match = GameManager.getInstance().getCurrentMatch();
        if (match == null) return ResponseUtils.error("Nessuna partita attiva", 500);
        
        if (match.getTimeLeft() <= 0) return ResponseUtils.error("Tempo scaduto", 408);
        if (session.isGameFinished()) return ResponseUtils.error("Hai già terminato la partita", 409);

        return processProposal(req.words, session, match);
    }

    private static String processProposal(List<String> userWords, ClientSession session, GameMatch match) {
        // Normalizzazione input
        List<String> normalizedProposal = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        Game.Group[] groups = match.getGameData().getGroups().toArray(new Game.Group[0]); // Array statico
        
        // 1. Validazione esistenza parole
        List<String> allGameWords = new ArrayList<>();
        for(Game.Group g : groups) allGameWords.addAll(g.getWords());
        
        if (!allGameWords.containsAll(normalizedProposal)) {
            return ResponseUtils.error("Parole non valide per questa partita.", 410);
        }
        
        // 2. Validazione parole già indovinate
        for (String theme : session.getGuessedThemes()) {
            for (Game.Group g : groups) {
                if (g.getTheme().equals(theme)) {
                    for (String w : normalizedProposal) {
                        if (g.getWords().contains(w)) {
                            return ResponseUtils.error("Parola già usata nel gruppo: " + theme, 411);
                        }
                    }
                }
            }
        }
        
        // 3. Logica Core: Verifica Gruppo
        boolean found = false;
        Game.Group foundGroup = null;

        for (Game.Group group : groups) {
            if (session.isThemeGuessed(group.getTheme())) continue;
            
            // Confronto insiemi (contiene tutti e contenuto da tutti = identici)
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
             
             // VITTORIA (3 gruppi trovati -> il 4° è automatico)
             if (session.getScore() == 3) {
                 for (Game.Group g : groups) {
                     if (!session.isThemeGuessed(g.getTheme())) {
                         session.addGuessedTheme(g.getTheme()); 
                         break;
                     }
                 }
                 
                 int rankingPoints = calculateRankingPoints(3, session.getErrors());
                 
                 UserManager.getInstance().updateGameResult(
                     session.getUsername(), rankingPoints, session.getErrors(), true
                 );
                 
                 session.setGameFinished(true);
                 message = "VITTORIA!";
                 isFinished = true;
             }
        } else {
             session.incrementErrors();
             isCorrect = false;
             message = "Sbagliato.";
             
             // SCONFITTA
             if (session.getErrors() >= ServerConfig.MAX_ERRORS) {
                 int rankingPoints = calculateRankingPoints(session.getScore(), session.getErrors());
                 
                 UserManager.getInstance().updateGameResult(
                     session.getUsername(), rankingPoints, session.getErrors(), false
                 );
                 
                 session.setGameFinished(true);
                 message = "HAI PERSO (Troppi errori)";
                 isFinished = true;
             }
        }
        
        ServerResponse.Proposal resp = new ServerResponse.Proposal(isCorrect, groupTitle, session.getScore());
        resp.message = message;
        
        if (isFinished) {
            resp.isFinished = true;
            resp.solution = InfoHandler.buildSolution(match.getGameData());
        }

        return ResponseUtils.toJson(resp);
    }
    
    public static int calculateRankingPoints(int groupsFound, int errors) {
        return (groupsFound * 6) - (errors * 4);
    }
}