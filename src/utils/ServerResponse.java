package utils;

import java.util.List;

public class ServerResponse {
    public String objectCode;
    public String status;
    public String message;
    public Integer errorCode;

    public ServerResponse(String objectCode, String status, String message) {
        this.objectCode = objectCode;
        this.status = status;
        this.message = message;
    }

    public ServerResponse() {
    }

    // --- ERROR & GENERIC ---
    public static class Error extends ServerResponse {
        public Error(String m, int c) {
            super("RES_ERROR", "ERROR", m);
            errorCode = c;
        }
    }

    public static class Generic extends ServerResponse {
        public Generic(String m) {
            super("RES_GENERIC", "OK", m);
        }
    }

    // --- EVENT ---
    public static class Event extends ServerResponse {
        public Boolean isFinished;

        public Event(String m) {
            super("RES_EVENT", "EVENT", m);
        }
    }

    // --- AUTH ---
    public static class Auth extends ServerResponse {
        public GameInfoData gameInfo;

        public Auth(String m, GameInfoData i) {
            super("RES_AUTH", "OK", m);
            gameInfo = i;
        }
    }

    // --- PROPOSAL ---
    public static class Proposal extends ServerResponse {
        public Boolean isCorrect;
        public String groupTitle;
        public Integer currentScore;
        public List<GroupData> solution;
        public Boolean isFinished;

        public Proposal(boolean c, String t, int s) {
            super("RES_PROPOSAL", "OK", c ? "Corretto" : "Sbagliato");
            isCorrect = c;
            groupTitle = t;
            currentScore = s;
        }
    }

    // --- INFO PARTITA ---
    public static class GameInfoData extends ServerResponse {
        public Integer gameId, timeLeft, mistakes, currentScore;
        public Boolean isFinished;
        public Boolean isWinner; 

        public List<String> words;
        public List<GroupData> correctGroups;
        public List<GroupData> solution;
        public List<PlayerResult> playerResults;

        public GameInfoData(String m) {
            super("RES_GAME_INFO", "OK", m);
        }
    }

    // --- STATS ---
    public static class GameStats extends ServerResponse {
        public Integer gameId, timeLeft, playersActive, playersFinished, playersWon;
        public Float averageScore;

        public GameStats() {
            super("RES_GAME_STATS", "OK", null);
        }
    }

    public static class PlayerStats extends ServerResponse {
        public Integer puzzlesCompleted, currentStreak, maxStreak, perfectPuzzles;
        public Float winRate, lossRate;
        
        // Istogramma delle sole VITTORIE per numero di errori [0..4]
        // Le sconfitte non sono incluse in questo array.
        public int[] mistakeHistogram; 

        public PlayerStats() {
            super("RES_PLAYER_STATS", "OK", null);
        }
    }

    // --- LEADERBOARD ---
    public static class Leaderboard extends ServerResponse {
        public List<RankingEntry> ranking;

        public Leaderboard(List<RankingEntry> r) {
            super("RES_LEADERBOARD", "OK", null);
            ranking = r;
        }
    }

    // --- ADMIN ---
    public static class AdminInfo extends ServerResponse {
        public String adminPayload;
        public List<UserAccountInfo> userList;
        public List<GroupData> oracleData;

        public AdminInfo() {
            super("RES_ADMIN", "OK", "Admin");
        }

        public AdminInfo(List<UserAccountInfo> u) {
            super("RES_ADMIN", "OK", "God");
            userList = u;
        }
    }

    // --- DTOs ---
    public static class UserAccountInfo {
        public String username;
        public String password;
        public int totalScore;
        public int played;
        public int won;

        public UserAccountInfo(String u, String p, int s, int pl, int w) {
            username = u;
            password = p;
            totalScore = s;
            played = pl;
            won = w;
        }
    }

    public static class GroupData {
        public String theme;
        public List<String> words;

        public GroupData(String t, List<String> w) {
            theme = t;
            words = w;
        }
    }

    public static class RankingEntry {
        public int position;
        public String username;
        public int score;

        public RankingEntry(int p, String u, int s) {
            position = p;
            username = u;
            score = s;
        }
    }

    public static class PlayerResult {
        public String username;
        public int score;
        public boolean won;

        public PlayerResult(String u, int s, boolean w) {
            username = u;
            score = s;
            won = w;
        }
    }
}