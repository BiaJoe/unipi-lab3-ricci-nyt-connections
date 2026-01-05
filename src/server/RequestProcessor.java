package server;

import com.google.gson.Gson;
import server.models.ClientSession;
import server.models.Game;
import server.models.Group;
import server.models.LiveStats;
import server.models.UserStats;
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
                        Game current = GameManager.getInstance().getCurrentGame();
                        ServerResponse resp = new ServerResponse();
                        resp.status = "OK";
                        resp.message = "Login effettuato";
                        resp.gameInfo = buildGameInfo(current, session);
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
                    if (GameManager.getInstance().getTimeLeft() <= 0) return error("Tempo scaduto", 408);
                    if (session.isGameFinished()) return error("Hai già terminato la partita", 409);

                    return processProposal(req.words, session, GameManager.getInstance().getCurrentGame());
                }

                case "requestGameInfo": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    return getGameInfoResponse(GameManager.getInstance().getCurrentGame(), session);
                }
                
                case "requestGameStats": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    Game currentG = GameManager.getInstance().getCurrentGame();
                    int gId = (req.gameId != null && req.gameId != 0) ? req.gameId : (currentG != null ? currentG.getGameId() : -1);
                    
                    if (currentG == null || currentG.getGameId() != gId) return error("Partita non attiva", 404);

                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    resp.gameId = gId;
                    LiveStats stats = GameManager.getInstance().calculateStats(server.getAllSessions());
                    resp.playersActive = stats.active;
                    resp.playersFinished = stats.finished;
                    resp.playersWon = stats.won;
                    resp.timeLeft = GameManager.getInstance().getTimeLeft();
                    return gson.toJson(resp);
                }

                case "requestPlayerStats": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    UserStats stats = UserManager.getInstance().getUserStats(session.getUsername());
                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    if (stats != null) {
                        resp.puzzlesCompleted = stats.puzzlesPlayed;
                        resp.puzzlesWon = stats.puzzlesWon;
                        resp.currentStreak = stats.currentStreak;
                        resp.maxStreak = stats.maxStreak;
                        resp.mistakeHistogram = stats.winDistribution;
                        if (stats.puzzlesPlayed > 0) {
                            resp.winRate = (float)stats.puzzlesWon / stats.puzzlesPlayed * 100;
                            resp.lossRate = 100.0f - resp.winRate;
                        }
                    }
                    return gson.toJson(resp);
                }

                case "requestLeaderboard": {
                    if (!session.isLoggedIn()) return error("Non loggato", 401);
                    ServerResponse resp = new ServerResponse();
                    resp.status = "OK";
                    resp.rankings = UserManager.getInstance().getLeaderboard();
                    for (int i = 0; i < resp.rankings.size(); i++) {
                        resp.rankings.get(i).position = i + 1;
                        if (resp.rankings.get(i).username.equals(session.getUsername())) resp.yourPosition = i + 1;
                    }
                    return gson.toJson(resp);
                }

                default: return error("Operazione non supportata", 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error("Errore interno", 500);
        }
    }

    private static String processProposal(List<String> userWords, ClientSession session, Game game) {
        if (game == null) return error("Nessuna partita in corso", 500);

        List<String> normalizedProposal = userWords.stream().map(String::toUpperCase).collect(Collectors.toList());
        List<String> allGameWords = new ArrayList<>();
        for(Group g : game.getGroups()) allGameWords.addAll(g.getWords());
        
        if (!allGameWords.containsAll(normalizedProposal)) {
            return error("Proposta non valida: parole non presenti.", 410);
        }
        
        // Check parole già usate
        for (String theme : session.getGuessedThemes()) {
            for(Group g : game.getGroups()) {
                if(g.getTheme().equals(theme)) {
                    for(String w : normalizedProposal) {
                        if(g.getWords().contains(w)) return error("Parola già usata.", 411);
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
             // Allega griglia aggiornata (coi buchi)
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
        
        resp.currentMistakes = session.getErrors();
        resp.currentScore = session.getScore();
        return gson.toJson(resp);
    }

    // --- MODIFICA CRUCIALE PER LA GRIGLIA PERSISTENTE ---
    public static ServerResponse.GameInfo buildGameInfo(Game g, ClientSession session) {
        ServerResponse.GameInfo info = new ServerResponse.GameInfo();
        if (g == null) return info;

        info.gameId = g.getGameId();
        info.mistakes = session.getErrors();
        info.currentScore = session.getScore(); 
        info.timeLeft = GameManager.getInstance().getTimeLeft();

        // 1. Recupera o crea lo shuffle persistente
        List<String> personalList = session.getPersonalShuffledWords();
        if (personalList == null || personalList.isEmpty()) {
            personalList = new ArrayList<>();
            for (Group group : g.getGroups()) {
                personalList.addAll(group.getWords());
            }
            Collections.shuffle(personalList);
            session.setPersonalShuffledWords(personalList);
        }

        // 2. Costruisci la lista da visualizzare (con buchi)
        List<String> displayWords = new ArrayList<>();
        
        for (String word : personalList) {
            boolean isGuessed = false;
            // Controlla se la parola appartiene a un gruppo già indovinato
            for (Group group : g.getGroups()) {
                if (session.isThemeGuessed(group.getTheme()) && group.getWords().contains(word)) {
                    isGuessed = true;
                    break;
                }
            }

            if (isGuessed) {
                displayWords.add(""); // STRINGA VUOTA AL POSTO DELLA PAROLA
            } else {
                displayWords.add(word);
            }
        }

        info.words = displayWords;
        return info;
    }

    private static String getGameInfoResponse(Game g, ClientSession session) {
        if (g == null) return error("Nessuna partita attiva", 404);

        ServerResponse resp = new ServerResponse();
        resp.status = "OK";
        resp.gameId = g.getGameId();
        resp.timeLeft = GameManager.getInstance().getTimeLeft();
        boolean userFinished = session.isGameFinished();
        resp.isFinished = (resp.timeLeft <= 0) || userFinished;
        resp.mistakes = session.getErrors();
        resp.currentScore = session.getScore();
        
        // Usa la logica centralizzata per la griglia
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
        return gson.toJson(resp);
    }
    
    private static String success(String msg, Object data) {
        ServerResponse r = new ServerResponse(); r.status = "OK"; r.message = msg; return gson.toJson(r);
    }
    private static String error(String msg, int code) {
        ServerResponse r = new ServerResponse(); r.status = "ERROR"; r.message = msg; r.errorCode = code; return gson.toJson(r);
    }
}