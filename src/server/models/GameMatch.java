package server.models;

import server.ServerConfig;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// IMPORTANTE: La classe deve chiamarsi GameMatch, non PlayedGame!
public class GameMatch {
    // Riferimento al "Blueprint"
    private final Game gameData;
    
    // Metadati Istanza
    private final int runNumber;
    private final String playedAt;
    private final long startTimeMillis;

    // Stato Giocatori (Key: Username -> Value: State)
    private final ConcurrentHashMap<String, PlayerGameState> players;

    public GameMatch(Game gameData, int runNumber) {
        this.gameData = gameData;
        this.runNumber = runNumber;
        this.playedAt = LocalDateTime.now().toString();
        this.startTimeMillis = System.currentTimeMillis();
        this.players = new ConcurrentHashMap<>();
    }

    // --- LOGICA DI GIOCO ---

    public PlayerGameState getOrCreatePlayerState(String username) {
        return players.computeIfAbsent(username, k -> new PlayerGameState());
    }
    
    public PlayerGameState getPlayerState(String username) {
        return players.get(username);
    }
    
    public int getTimeLeft() {
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        int remaining = (int) (ServerConfig.GAME_DURATION - elapsed);
        return Math.max(0, remaining);
    }

    // Sostituisce la classe LiveStats inutile
    public StatsSnapshot getStatsSnapshot() {
        int active = 0, finished = 0, won = 0;
        for (PlayerGameState p : players.values()) {
            if (p.isFinished()) {
                finished++;
                if (p.getErrors() < ServerConfig.MAX_ERRORS) won++;
            } else {
                active++;
            }
        }
        return new StatsSnapshot(active, finished, won);
    }

    // --- GETTERS ---
    public Game getGameData() { return gameData; }
    public int getRunNumber() { return runNumber; }
    public String getPlayedAt() { return playedAt; }
    public Map<String, PlayerGameState> getPlayers() { return players; }
    public int getGameId() { return gameData.getGameId(); }

    // DTO Interno per passare i dati al Handler senza file extra
    public static class StatsSnapshot {
        public int active, finished, won;
        public StatsSnapshot(int a, int f, int w) { active=a; finished=f; won=w; }
    }
}