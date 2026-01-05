package utils;

import java.util.List;

public class ServerResponse {
    // Campi comuni
    public String status;      // "OK" o "ERROR"
    public String message;     // Descrizione
    public Integer errorCode;  // Codice errore

    public Integer gameId;
    // --- Campi specifici per LOGIN (gameInfo) ---
    public GameInfo gameInfo;

    // --- Campi specifici per SUBMIT PROPOSAL ---
    public Boolean isCorrect;
    public String groupTitle;
    public Integer currentMistakes;
    public Integer currentScore;

    // --- Campi specifici per REQUEST GAME INFO ---
    public Boolean isFinished;
    public Integer timeLeft;
    public List<String> wordsToGroup;
    public List<GroupData> correctGroups; // Gruppi già indovinati
    public Integer mistakes;
    public List<GroupData> solution; // Soluzione completa (se gioco finito)

    // --- Campi specifici per REQUEST GAME STATS ---
    public Integer playersActive;
    public Integer playersFinished;
    public Integer playersWon;
    public Float averageScore; // Nota: nel PDF è FLOAT

    // --- Campi specifici per LEADERBOARD ---
    public List<RankingEntry> rankings;
    public Integer yourPosition;
    
    // --- Campi specifici per PLAYER STATS (NYT Style) ---
    public Integer puzzlesCompleted;
    public Integer puzzlesWon;
    public Float winRate;
    public Float lossRate;
    public Integer currentStreak;
    public Integer maxStreak;
    public Integer perfectPuzzles;
    public int[] mistakeHistogram;

    // --- Classi interne per strutturare i dati complessi ---
    
    public static class GameInfo {
        public int gameId;
        public List<String> words;
        public List<List<String>> alreadyGuessed; // Liste di parole
        public int mistakes;
        public int timeLeft;
        public int currentScore;
    }

    public static class GroupData {
        public String title;
        public List<String> words;
        
        public GroupData(String t, List<String> w) { this.title = t; this.words = w; }
    }

    public static class RankingEntry {
        public int position;
        public String username;
        public int score;
        
        public RankingEntry(int p, String u, int s) { this.position = p; this.username = u; this.score = s; }
    }
}