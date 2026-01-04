package utils;
import java.util.List;

public class ClientRequest {
    public String operation;    // "login", "submitProposal", "requestLeaderboard", etc.
    public String username;
    public String password;
    public Integer gameId;
    public List<String> words;
    
    // Campi aggiuntivi utili per le richieste del PDF
    public String playerName;   // Se chiedi stats di un altro giocatore
    public Integer topPlayers;  // Se chiedi la classifica top K

    public ClientRequest() {} 
}