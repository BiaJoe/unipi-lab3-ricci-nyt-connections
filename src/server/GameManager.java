package server;

import server.models.Game;
import server.models.LiveStats;
import server.models.PlayerGameState;

import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static GameManager instance;
    
    private Game currentGame;
    private long gameStartTime;
    
    // MAPPA FONDAMENTALE: Username -> StatoPartita
    // Resiste ai logout. Viene cancellata solo quando cambia il gioco.
    private ConcurrentHashMap<String, PlayerGameState> activePlayers;

    private GameManager() {
        activePlayers = new ConcurrentHashMap<>();
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) instance = new GameManager();
        return instance;
    }

    public synchronized void setCurrentGame(Game g) {
        this.currentGame = g;
        this.activePlayers.clear(); // NUOVA PARTITA -> RESET TOTALE MEMORIA GIOCATORI
        this.resetGameTimer();
    }

    public synchronized Game getCurrentGame() { return currentGame; }

    public synchronized void resetGameTimer() {
        this.gameStartTime = System.currentTimeMillis();
    }

    public int getTimeLeft() {
        if (gameStartTime == 0) return 0;
        long elapsed = (System.currentTimeMillis() - gameStartTime) / 1000;
        int remaining = (int) (ServerConfig.GAME_DURATION - elapsed);
        return Math.max(0, remaining);
    }

    // --- GESTIONE STATO GIOCATORI ---
    
    // Chiamato al LOGIN: recupera lo stato se esiste, altrimenti ne crea uno nuovo
    public PlayerGameState getOrCreatePlayerState(String username) {
        return activePlayers.computeIfAbsent(username, k -> new PlayerGameState());
    }

    // --- CALCOLO STATISTICHE LIVE ---
    // Ora calcoliamo le stats basandoci sulla mappa activePlayers (più accurato)
    public LiveStats calculateStats() {
        LiveStats stats = new LiveStats();
        
        for (PlayerGameState pState : activePlayers.values()) {
            if (pState.isFinished()) {
                stats.finished++;
                if (pState.getErrors() < ServerConfig.MAX_ERRORS) {
                    stats.won++;
                }
            } else {
                stats.active++;
            }
        }
        return stats;
    }
}