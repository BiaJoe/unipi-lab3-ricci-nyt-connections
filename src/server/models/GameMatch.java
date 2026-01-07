package server.models;

import server.ServerConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections; // Importante
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameMatch {
    private final Game gameData;
    private final int runNumber;
    private final String playedAt;
    private final long startTimeMillis;
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
        return players.computeIfAbsent(username, k -> {
            PlayerGameState ps = new PlayerGameState();
            
            // --- FIX SHUFFLE ---
            // Recuperiamo tutte le parole, le mescoliamo UNA volta e le salviamo nello stato
            List<String> allWords = new ArrayList<>();
            for (Game.Group g : gameData.getGroups()) {
                allWords.addAll(g.getWords());
            }
            Collections.shuffle(allWords);
            ps.setShuffledWords(allWords);
            // -------------------
            
            return ps;
        });
    }
    
    public PlayerGameState getPlayerState(String username) {
        return players.get(username);
    }
    
    public int getTimeLeft() {
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        int remaining = (int) (ServerConfig.GAME_DURATION - elapsed);
        return Math.max(0, remaining);
    }

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

    public Game getGameData() { return gameData; }
    public int getRunNumber() { return runNumber; }
    public String getPlayedAt() { return playedAt; }
    public Map<String, PlayerGameState> getPlayers() { return players; }
    public int getGameId() { return gameData.getGameId(); }

    public static class StatsSnapshot {
        public int active, finished, won;
        public StatsSnapshot(int a, int f, int w) { active=a; finished=f; won=w; }
    }
}