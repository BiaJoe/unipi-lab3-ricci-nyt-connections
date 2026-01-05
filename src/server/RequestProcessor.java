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
            // Parsing della richiesta nella classe DTO
            ClientRequest req = gson.fromJson(jsonText, ClientRequest.class);
            
            // Fix per evitare NullPointerException se il JSON è vuoto o malformato
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
                    else return error("Errore aggiornamento (password errata o user esistente)", 403);
                }

                case "login": {
                    if (session.isLoggedIn()) return error("Già loggato", 405);
                    if (UserManager.getInstance().login(req.username, req.psw)) {
                        session.setUsername(req.username);
                        session.setLoggedIn(true);
                        
                        // Costruzione GameInfo per la risposta
                        ServerResponse resp = new ServerResponse();
                        resp.status = "OK";
                        resp.message = "Login effettuato";
                        // Passiamo il server per calcolare il tempo
                        resp.gameInfo = buildGameInfo(server.getCurrentGame(), session, server);
                        return gson.toJson(resp);
                    }
                    return error("Credenziali errate", 401);
                }

                case "logout": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    session.setLoggedIn(false);
                    session.setUsername(null);
                    return success("Logout effettuato", null);
                }

                case "submitProposal": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    if (req.words == null || req.words.size() != 4) return error("Servono 4 parole", 400);
                    
                    // Controllo se il tempo è scaduto PRIMA di elaborare
                    if (server.getTimeLeft() <= 0) {
                         return error("Tempo scaduto per questa partita!", 408);
                    }

                    return processProposal(req.words, session, server.getCurrentGame());
                }

                case "requestGameInfo": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    return getGameInfoResponse(server.getCurrentGame(), session, server);
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
                    } else {
                        resp.winRate = 0f; resp.lossRate = 0f;
                    }
                    return gson.toJson(resp);
                }
                
                case "requestLeaderboard": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    resp.rankings = UserManager.getInstance().getLeaderboard();
                    // Calcolo posizione utente corrente
                    for (int i = 0; i < resp.rankings.size(); i++) {
                        resp.rankings.get(i).position = i + 1; // Assegna posizioni
                        if (resp.rankings.get(i).username.equals(session.getUsername())) {
                            resp.yourPosition = i + 1;
                        }
                    }
                    return gson.toJson(resp);
                }

                default:
                    return error("Operazione non supportata: " + req.operation, 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("Errore interno: " + e.getMessage(), 500);
        }
    }

    // --- HELPER LOGIC ---

    private static ServerResponse.GameInfo buildGameInfo(Game g, ClientSession session, ServerMain server) {
        ServerResponse.GameInfo info = new ServerResponse.GameInfo();
        if (g == null) return info; // Evita crash se server appena partito

        info.gameId = g.getGameId();
        info.mistakes = session.getErrors();
        info.currentScore = session.getScore(); 
        
        // Calcolo tempo reale
        info.timeLeft = server.getTimeLeft();

        List<String> visibleWords = new ArrayList<>();
        // Mostra solo parole di gruppi NON indovinati
        for (Group group : g.getGroups()) {
            if (!session.isThemeGuessed(group.getTheme())) {
                visibleWords.addAll(group.getWords());
            }
        }
        Collections.shuffle(visibleWords);
        info.words = visibleWords;
        
        // Popola alreadyGuessed (List<List<String>>) se necessario, qui semplificato
        info.alreadyGuessed = new ArrayList<>(); 
        
        return info;
    }

    private static String getGameInfoResponse(Game g, ClientSession session, ServerMain server) {
        if (g == null) return error("Nessuna partita attiva", 404);

        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.gameId = g.getGameId(); // Assicurati che ServerResponse abbia questo campo!
        
        int timeLeft = server.getTimeLeft();
        resp.timeLeft = timeLeft;
        
        // La partita è finita se il tempo è scaduto
        // OPPURE se l'utente ha vinto (4 gruppi) 
        // OPPURE se l'utente ha perso (max errori)
        boolean userFinished = (session.getScore() == 4) || (session.getErrors() >= ServerMain.maxErrors);
        resp.isFinished = (timeLeft <= 0) || userFinished;
        
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
        resp.wordsToGroup = words; // Parole rimaste da raggruppare
        
        // Se la partita è finita (per tempo o per l'utente), invia la SOLUZIONE completa
        if (resp.isFinished) {
            resp.solution = new ArrayList<>();
            for (Group gr : g.getGroups()) {
                resp.solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
            }
        }
        
        return gson.toJson(resp);
    }

    private static String processProposal(List<String> userWords, ClientSession session, Game game) {
        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        
        List<String> normalized = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        
        boolean found = false;
        for (Group group : game.getGroups()) {
            if (session.isThemeGuessed(group.getTheme())) continue;
            
            // Check se le parole coincidono (indipendentemente dall'ordine)
            if (group.getWords().containsAll(normalized) && normalized.containsAll(group.getWords())) {
                session.addGuessedTheme(group.getTheme());
                resp.isCorrect = true;
                resp.groupTitle = group.getTheme();
                
                // VITTORIA
                if (session.getScore() == 4) {
                    UserManager.getInstance().updateStatsWin(session.getUsername(), session.getErrors());
                    resp.message = "VITTORIA!";
                }
                found = true;
                break;
            }
        }

        if (found) {
             // ...
             if (session.getScore() == 4) {
                 UserManager.getInstance().updateStatsWin(session.getUsername(), session.getErrors());
                 session.setGameFinished(true); // <--- NUOVO
                 resp.message = "VITTORIA!";
             }
             // ...
        } else {
             // ...
             if (session.getErrors() >= ServerMain.maxErrors) {
                 UserManager.getInstance().updateStatsLoss(session.getUsername());
                 session.setGameFinished(true); // <--- NUOVO
                 resp.message = "HAI PERSO";
             }
        }
        
        resp.currentMistakes = session.getErrors();
        resp.currentScore = session.getScore();
        return gson.toJson(resp);
    }

    private static String success(String msg, Object data) {
        ServerResponse r = new ServerResponse();
        r.status = "OK";
        r.message = msg;
        return gson.toJson(r);
    }
    
    private static String error(String msg, int code) {
        ServerResponse r = new ServerResponse();
        r.status = "ERROR";
        r.message = msg;
        r.errorCode = code;
        return gson.toJson(r);
    }
}