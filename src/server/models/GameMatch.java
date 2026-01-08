package server.models;

import server.ServerConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    public PlayerGameState getOrCreatePlayerState(String username) {
        return players.computeIfAbsent(username, k -> {
            PlayerGameState ps = new PlayerGameState();
            List<String> allWords = new ArrayList<>();
            for (Game.Group g : gameData.getGroups()) {
                allWords.addAll(g.getWords());
            }
            Collections.shuffle(allWords);
            ps.setShuffledWords(allWords);
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

    // --- SNAPSHOT CON MEDIA E VITTORIA CORRETTA ---
    public StatsSnapshot getStatsSnapshot() {
        int active = 0, finished = 0, won = 0;
        float totalScore = 0;

        for (PlayerGameState p : players.values()) {
            if (p.isFinished()) {
                finished++;
                totalScore += p.getScore();
                
                // Usa il flag impostato da GameHandler
                if (p.hasWon()) { 
                    won++;
                }
            } else {
                active++;
            }
        }
        
        float avg = (finished > 0) ? (totalScore / finished) : 0.0f;
        return new StatsSnapshot(active, finished, won, avg);
    }

    public Game getGameData() { return gameData; }
    public int getRunNumber() { return runNumber; }
    public String getPlayedAt() { return playedAt; }
    public Map<String, PlayerGameState> getPlayers() { return players; }
    public int getGameId() { return gameData.getGameId(); }

    public static class StatsSnapshot {
        public int active, finished, won;
        public float averageScore;
        
        public StatsSnapshot(int a, int f, int w, float avg) { 
            active=a; finished=f; won=w; averageScore=avg;
        }
    }
}