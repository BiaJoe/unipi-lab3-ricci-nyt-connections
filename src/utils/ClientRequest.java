package utils;

import java.util.List;

// Classe "contenitore" per tutte le possibili richieste del client
public class ClientRequest {
    public String operation;

    // Campi per Login/Register/Update
    public String name;      // Per register
    public String username;  // Per login
    public String psw;       // Password (nome specifico richiesto dal PDF)
    
    // Campi per UpdateCredentials
    public String oldName;
    public String newName;
    public String oldPsw;
    public String newPsw;

    // Campi per SubmitProposal
    public List<String> words;

    // Campi per RequestGameInfo / RequestGameStats
    public Integer gameId; // Integer così può essere null

    // Campi per RequestLeaderboard
    public String playerName;
    public Integer topPlayers;
}