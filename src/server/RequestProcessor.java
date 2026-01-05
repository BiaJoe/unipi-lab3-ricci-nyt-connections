package server;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RequestProcessor {
    private static final Gson gson = new Gson();

    public static String process(String jsonText, ClientSession session, ServerMain server) {
        try {
            ClientRequest req = gson.fromJson(jsonText, ClientRequest.class);
            if (req == null || req.operation == null) return error("Manca operation", 400);

            switch (req.operation) {
                case "register": {
                    if (req.name == null || req.psw == null) return error("Dati mancanti", 401);
                    boolean ok = UserManager.getInstance().register(req.name, req.psw);
                    if (ok) return success("Registrazione avvenuta", null);
                    else return error("Username già in uso", 402);
                }

                case "updateCredentials": {
                    boolean ok = UserManager.getInstance().updateCredentials(req.oldName, req.newName, req.oldPsw, req.newPsw);
                    if (ok) return success("Credenziali aggiornate", null);
                    else return error("Errore aggiornamento", 403);
                }

                case "login": {
                    if (session.isLoggedIn()) return error("Già loggato", 405);
                    if (UserManager.getInstance().login(req.username, req.psw)) {
                        session.setUsername(req.username);
                        session.setLoggedIn(true);
                        
                        ServerResponse resp = new ServerResponse();
                        resp.status = "OK";
                        resp.message = "Login effettuato";
                        resp.gameInfo = buildGameInfo(server.getCurrentGame(), session, server);
                        return gson.toJson(resp);
                    }
                    return error("Credenziali errate", 401);
                }

                case "logout": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    session.setLoggedIn(false);
                    session.setUsername(null);
                    session.resetGameStatus(); 
                    return success("Logout effettuato", null);
                }

                case "submitProposal": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    if (req.words == null || req.words.size() != 4) return error("Servono 4 parole", 400);
                    
                    if (server.getTimeLeft() <= 0) return error("Tempo scaduto", 408);
                    if (session.isGameFinished()) return error("Hai già terminato la partita", 409);

                    return processProposal(req.words, session, server.getCurrentGame());
                }

                case "requestGameInfo": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    return getGameInfoResponse(server.getCurrentGame(), session, server);
                }
                
                case "requestGameStats": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    int gId = (req.gameId != null) ? req.gameId : (server.getCurrentGame() != null ? server.getCurrentGame().getGameId() : -1);
                    
                    if (server.getCurrentGame() == null || server.getCurrentGame().getGameId() != gId) {
                         return error("Partita non trovata o non attiva", 404);
                    }

                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    resp.gameId = gId;
                    
                    ServerMain.GameStats stats = server.calculateCurrentGameStats();
                    resp.playersActive = stats.active;
                    resp.playersFinished = stats.finished;
                    resp.playersWon = stats.won;
                    resp.timeLeft = server.getTimeLeft();
                    
                    return gson.toJson(resp);
                }

                case "requestPlayerStats": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    UserStats stats = UserManager.getInstance().getUserStats(session.getUsername());
                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    
                    resp.puzzlesCompleted = stats.puzzlesPlayed;
                    resp.puzzlesWon = stats.puzzlesWon;
                    resp.currentStreak = stats.currentStreak;
                    resp.maxStreak = stats.maxStreak;
                    resp.mistakeHistogram = stats.winDistribution;
                    
                    if (stats.puzzlesPlayed > 0) {
                        resp.winRate = (float)stats.puzzlesWon / stats.puzzlesPlayed * 100;
                        resp.lossRate = 100.0f - resp.winRate;
                    } else { resp.winRate = 0f; resp.lossRate = 0f; }
                    return gson.toJson(resp);
                }

                case "requestLeaderboard": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    resp.rankings = UserManager.getInstance().getLeaderboard();
                    for (int i = 0; i < resp.rankings.size(); i++) {
                        resp.rankings.get(i).position = i + 1;
                        if (resp.rankings.get(i).username.equals(session.getUsername())) {
                            resp.yourPosition = i + 1;
                        }
                    }
                    return gson.toJson(resp);
                }

                default:
                    return error("Operazione non supportata", 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("Errore interno: " + e.getMessage(), 500);
        }
    }

    // --- HELPER METHODS ---

    private static String processProposal(List<String> userWords, ClientSession session, Game game) {
        // Normalizza
        List<String> normalizedProposal = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        List<String> allGameWords = new ArrayList<>();
        for(Group g : game.getGroups()) allGameWords.addAll(g.getWords());
        
        // Validazione (Nessuna penalità per parole non esistenti/malformate [cite: 126])
        if (!allGameWords.containsAll(normalizedProposal)) {
            return error("Proposta non valida: contiene parole non presenti nella partita.", 410);
        }
        
        for (String theme : session.getGuessedThemes()) {
            for(Group g : game.getGroups()) {
                if(g.getTheme().equals(theme)) {
                    for(String w : normalizedProposal) {
                        if(g.getWords().contains(w)) return error("Parola già usata in un gruppo corretto.", 411);
                    }
                }
            }
        }
        
        // Controllo Semantico
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
             
             // --- REGOLA: 3 Gruppi = Vittoria (l'ultimo è implicito)  ---
             if (session.getScore() == 3) {
                 // Trova e aggiungi il 4° gruppo mancante
                 for (Group g : game.getGroups()) {
                     if (!session.isThemeGuessed(g.getTheme())) {
                         session.addGuessedTheme(g.getTheme()); 
                         break;
                     }
                 }

                 // Vittoria!
                 UserManager.getInstance().updateStatsWin(session.getUsername(), session.getErrors());
                 session.setGameFinished(true);
                 resp.message = "VITTORIA!";
                 resp.isFinished = true; // Invia stato finito così il client può mostrare tutto
             }
        } else {
             // Errore: Penalità [cite: 20]
             session.incrementErrors();
             resp.isCorrect = false;
             resp.message = "Sbagliato.";
             
             // Sconfitta per max errori [cite: 10]
             if (session.getErrors() >= ServerMain.maxErrors) {
                 UserManager.getInstance().updateStatsLoss(session.getUsername());
                 session.setGameFinished(true);
                 resp.message = "HAI PERSO (Troppi errori)";
                 resp.isFinished = true;
             }
        }
        
        resp.currentMistakes = session.getErrors();
        resp.currentScore = session.getScore();
        return gson.toJson(resp);
    }

    // Costruzione GameInfo per il client (Public per uso in ServerMain)
    public static ServerResponse.GameInfo buildGameInfo(Game g, ClientSession session, ServerMain server) {
        ServerResponse.GameInfo info = new ServerResponse.GameInfo();
        if (g == null) return info;

        info.gameId = g.getGameId();
        info.mistakes = session.getErrors();
        info.currentScore = session.getScore(); 
        info.timeLeft = server.getTimeLeft();

        List<String> visibleWords = new ArrayList<>();
        for (Group group : g.getGroups()) {
            if (!session.isThemeGuessed(group.getTheme())) {
                visibleWords.addAll(group.getWords());
            }
        }
        Collections.shuffle(visibleWords);
        info.words = visibleWords;
        return info;
    }

    private static String getGameInfoResponse(Game g, ClientSession session, ServerMain server) {
        if (g == null) return error("Nessuna partita attiva", 404);

        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.gameId = g.getGameId();
        resp.timeLeft = server.getTimeLeft();
        
        boolean userFinished = session.isGameFinished();
        resp.isFinished = (resp.timeLeft <= 0) || userFinished;
        
        resp.mistakes = session.getErrors();
        resp.currentScore = session.getScore();
        
        List<String> words = new ArrayList<>();
        resp.correctGroups = new ArrayList<>();
        
        for (Group gr : g.getGroups()) {
            if (session.isThemeGuessed(gr.getTheme())) {
                resp.correctGroups.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            } else {
                words.addAll(gr.getWords());
            }
        }
        Collections.shuffle(words);
        resp.wordsToGroup = words; 
        
        if (resp.isFinished) {
            resp.solution = new ArrayList<>();
            for (Group gr : g.getGroups()) {
                resp.solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        return gson.toJson(resp);
    }
    
    private static String success(String msg, Object data) {
        ServerResponse r = new ServerResponse(); r.status = "OK"; r.message = msg; return gson.toJson(r);
    }
    private static String error(String msg, int code) {
        ServerResponse r = new ServerResponse(); r.status = "ERROR"; r.message = msg; r.errorCode = code; return gson.toJson(r);
    }
}