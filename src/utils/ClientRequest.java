package utils;

import java.util.List;

// CLASSE DTO PURA (Data Transfer Object)
// Non contiene logica di rete, solo la struttura dei dati.
public abstract class ClientRequest {
    public String operation;

    // --- SOTTOCLASSI SPECIFICHE ---

    public static class Register extends ClientRequest {
        public String name;
        public String psw;

        public Register(String name, String psw) {
            this.operation = "register";
            this.name = name;
            this.psw = psw;
        }
    }

    public static class Login extends ClientRequest {
        public String username;
        public String psw;
        public int udpPort;

        public Login(String username, String psw, int udpPort) {
            this.operation = "login";
            this.username = username;
            this.psw = psw;
            this.udpPort = udpPort;
        }
    }

    public static class Logout extends ClientRequest {
        public Logout() {
            this.operation = "logout";
        }
    }

    public static class UpdateCredentials extends ClientRequest {
        public String oldName;
        public String newName;
        public String oldPsw;
        public String newPsw;

        public UpdateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
            this.operation = "updateCredentials";
            this.oldName = oldName;
            this.newName = newName;
            this.oldPsw = oldPsw;
            this.newPsw = newPsw;
        }
    }

    public static class SubmitProposal extends ClientRequest {
        public List<String> words;

        public SubmitProposal(List<String> words) {
            this.operation = "submitProposal";
            this.words = words;
        }
    }

    public static class GameInfo extends ClientRequest {
        public Integer gameId;

        public GameInfo(int gameId) {
            this.operation = "requestGameInfo";
            this.gameId = gameId;
        }
    }

    public static class PlayerStats extends ClientRequest {
        public PlayerStats() {
            this.operation = "requestPlayerStats";
        }
    }

    public static class Leaderboard extends ClientRequest {
        public Integer gameId;

        public Leaderboard(int gameId) {
            this.operation = "requestLeaderboard";
            this.gameId = gameId;
        }
    }
}