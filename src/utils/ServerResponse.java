package utils;

import java.util.List;

public class ServerResponse {
    public String objectCode; // IL DISCRIMINATORE (es. RES_PLAYER_STATS)
    public String status;     // "OK", "ERROR"
    public String message;    // Messaggio opzionale
    public Integer errorCode;

    public ServerResponse(String objectCode, String status, String message) {
        this.objectCode = objectCode;
        this.status = status;
        this.message = message;
    }
    
    public ServerResponse() {}

    // --- 1. ERRORI & GENERICI ---
    public static class Error extends ServerResponse {
        public Error(String msg, int code) { 
            super("RES_ERROR", "ERROR", msg); 
            this.errorCode = code; 
        }
    }

    public static class Generic extends ServerResponse {
        public Generic(String msg) { super("RES_GENERIC", "OK", msg); }
    }

    // --- 2. EVENTI (UDP) ---
    public static class Event extends ServerResponse {
        public Boolean isFinished;
        public Event(String msg) { super("RES_EVENT", "EVENT", msg); }
    }

    // --- 3. AUTH (Login) ---
    public static class Auth extends ServerResponse {
        public GameInfoData gameInfo; // Riutilizziamo la struttura dati completa
        public Auth(String msg, GameInfoData info) { 
            super("RES_AUTH", "OK", msg); 
            this.gameInfo = info; 
        }
    }

    // --- 4. PROPOSTA (Submit) ---
    public static class Proposal extends ServerResponse {
        public Boolean isCorrect;
        public String groupTitle;
        public Integer currentScore;
        public List<GroupData> solution; // Se la partita finisce con questo submit
        public Boolean isFinished;

        public Proposal(boolean correct, String title, int score) {
            super("RES_PROPOSAL", "OK", correct ? "Gruppo Trovato!" : "Sbagliato.");
            this.isCorrect = correct;
            this.groupTitle = title;
            this.currentScore = score;
        }
    }

    // --- 5. INFO PARTITA (Stato corrente o storico) ---
    public static class GameInfoData extends ServerResponse {
        public Integer gameId;
        public Integer timeLeft;
        public Integer mistakes;
        public Integer currentScore;
        public Boolean isFinished;
        public List<String> words; // Parole mescolate o parziali
        public List<GroupData> correctGroups; // Gruppi già indovinati
        public List<GroupData> solution; // Soluzione completa (solo se finita)
        public List<PlayerResult> playerResults; // Storico partecipanti (solo se finita)

        public GameInfoData(String msg) {
            super("RES_GAME_INFO", "OK", msg);
        }
    }

    // --- 6. STATISTICHE PARTITA (Globali) ---
    public static class GameStats extends ServerResponse {
        public Integer gameId;
        // Se in corso:
        public Integer timeLeft;
        public Integer playersActive;
        // Se finita (o parziali):
        public Integer playersFinished;
        public Integer playersWon;
        public Float averageScore; // Solo se finita

        public GameStats() { super("RES_GAME_STATS", "OK", null); }
    }

    // --- 7. STATISTICHE GIOCATORE (Personali) ---
    public static class PlayerStats extends ServerResponse {
        public Integer puzzlesCompleted; //
        public Float winRate;            //
        public Float lossRate;           //
        public Integer currentStreak;    //
        public Integer maxStreak;        //
        public Integer perfectPuzzles;   //
        public int[] mistakeHistogram;   // (Array di 5 int: 0 err, 1 err... 4 err)

        public PlayerStats() { super("RES_PLAYER_STATS", "OK", null); }
    }

    // --- 8. CLASSIFICA (Leaderboard) ---
    public static class Leaderboard extends ServerResponse {
        public List<RankingEntry> ranking;

        public Leaderboard(List<RankingEntry> ranking) {
            super("RES_LEADERBOARD", "OK", null);
            this.ranking = ranking;
        }
    }

    // --- DTO DI SUPPORTO ---
    public static class GroupData {
        public String theme;
        public List<String> words;
        public GroupData(String t, List<String> w) { this.theme = t; this.words = w; }
    }

    public static class RankingEntry {
        public int position;
        public String username;
        public int score;
        public RankingEntry(int p, String u, int s) { this.position = p; this.username = u; this.score = s; }
    }

    public static class PlayerResult {
        public String username;
        public int errors;
        public boolean won;
        public PlayerResult(String u, int e, boolean w) { this.username = u; this.errors = e; this.won = w; }
    }
}