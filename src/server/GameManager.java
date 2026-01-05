package server;

import server.models.ClientSession;
import server.models.Game;
import server.models.LiveStats;

import java.util.Collection;

public class GameManager {
    private static GameManager instance;
    
    private Game currentGame;
    private long gameStartTime;

    // Singleton
    private GameManager() {}

    public static synchronized GameManager getInstance() {
        if (instance == null) instance = new GameManager();
        return instance;
    }

    // --- GESTIONE STATO PARTITA ---

    public synchronized void setCurrentGame(Game g) {
        this.currentGame = g;
        this.resetGameTimer();
    }

    public synchronized Game getCurrentGame() {
        return currentGame;
    }

    public synchronized void resetGameTimer() {
        this.gameStartTime = System.currentTimeMillis();
    }

    public int getTimeLeft() {
        if (gameStartTime == 0) return 0;
        long elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        int remaining = (int) (ServerConfig.GAME_DURATION - elapsed);
        return Math.max(0, remaining);
    }

    // --- CALCOLO STATISTICHE LIVE ---
    // Richiede la lista delle sessioni attive (che gli passerà il ServerMain o chi ha il Selector)
    public LiveStats calculateStats(Collection<ClientSession> sessions) {
        LiveStats stats = new LiveStats();
        
        for (ClientSession session : sessions) {
            if (session.isLoggedIn()) {
                if (session.isGameFinished()) {
                    stats.finished++;
                    // Logica vittoria: meno errori del massimo consentito
                    if (session.getErrors() < ServerConfig.MAX_ERRORS) {
                        stats.won++;
                    }
                } else {
                    stats.active++;
                }
            }
        }
        return stats;
    }
}