package utils;

import java.util.List;

public abstract class ClientRequest {
    public String operation;

    // --- 1. GESTIONE UTENTE ---
    public static class Register extends ClientRequest {
        public String name; public String psw;
        public Register(String name, String psw) { this.operation = "register"; this.name = name; this.psw = psw; }
    }
    public static class Login extends ClientRequest {
        public String username; public String psw; public int udpPort;
        public Login(String u, String p, int port) { this.operation = "login"; this.username = u; this.psw = p; this.udpPort = port; }
    }
    public static class Logout extends ClientRequest {
        public Logout() { this.operation = "logout"; }
    }
    public static class UpdateCredentials extends ClientRequest {
        public String oldName, newName, oldPsw, newPsw;
        public UpdateCredentials(String on, String nn, String op, String np) {
            this.operation = "updateCredentials"; this.oldName = on; this.newName = nn; this.oldPsw = op; this.newPsw = np;
        }
    }

    // --- 2. GESTIONE PARTITA ---
    public static class SubmitProposal extends ClientRequest {
        public List<String> words;
        public SubmitProposal(List<String> w) { this.operation = "submitProposal"; this.words = w; }
    }
    
    // MODIFICATO: Supporta richiesta senza ID (corrente) e con ID (specifica)
    public static class GameInfo extends ClientRequest {
        public Integer gameId;
        
        // Costruttore per ID specifico (es: /gi 0)
        public GameInfo(int id) { this.operation = "requestGameInfo"; this.gameId = id; }
        
        // Costruttore per PARTITA CORRENTE (es: /i) -> gameId è null
        public GameInfo() { this.operation = "requestGameInfo"; this.gameId = null; }
    }
    
    // MODIFICATO: Supporta richiesta senza ID (corrente) e con ID (specifica)
    public static class RequestGameStats extends ClientRequest {
        public Integer gameId;
        
        // Costruttore per ID specifico (es: /gs 0)
        public RequestGameStats(int id) { this.operation = "requestGameStats"; this.gameId = id; }
        
        // Costruttore per PARTITA CORRENTE (es: /gs) -> gameId è null
        public RequestGameStats() { this.operation = "requestGameStats"; this.gameId = null; }
    }

    // --- 3. INFORMAZIONI GENERALI ---
    public static class PlayerStats extends ClientRequest {
        public PlayerStats() { this.operation = "requestPlayerStats"; }
    }
    public static class Leaderboard extends ClientRequest {
        public String playerName; public Integer topPlayers;
        public Leaderboard() { this.operation = "requestLeaderboard"; }
        public Leaderboard(int k) { this.operation = "requestLeaderboard"; this.topPlayers = k; }
        public Leaderboard(String name) { this.operation = "requestLeaderboard"; this.playerName = name; }
    }

    // --- 4. COMANDI ADMIN (HIDDEN) ---
    public static class Oracle extends ClientRequest {
        public String password;
        public Oracle(String password) { this.operation = "oracle"; this.password = password; }
    }
    public static class God extends ClientRequest {
        public String password;
        public God(String password) { this.operation = "god"; this.password = password; }
    }
}