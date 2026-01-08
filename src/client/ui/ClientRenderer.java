package client.ui;

import client.logic.Command;
import utils.ServerResponse;
import java.util.Collection;

public interface ClientRenderer {
    void showHelp(Collection<Command> commands);
    void showMessage(String msg);
    void showError(String err);
    
    // Mostra lo stato del gioco (Griglia parole, errori, tempo)
    void showGameInfo(ServerResponse.GameInfoData info);
    
    // Risultato di un tentativo (Esatto/Sbagliato)
    void showSubmitResult(ServerResponse.Proposal proposal);
    
    // Mostra statistiche globali della partita (chi sta giocando, chi ha vinto)
    void showGameStats(ServerResponse.GameStats stats);
    
    // Mostra statistiche personali (Istogramma, Win rate)
    void showPlayerStats(ServerResponse.PlayerStats stats);
    
    // Mostra classifica
    void showLeaderboard(ServerResponse.Leaderboard leaderboard);
    
    // Notifiche asincrone
    void showNotification(String message);

    // Mostra info amministrazione (Utenti, Oracle, Payload generici)
    void showAdminInfo(ServerResponse.AdminInfo info);
}