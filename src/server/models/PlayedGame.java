package server.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayedGame {
    // I campi final garantiscono la visibilità tra thread dopo la costruzione
    private final Game gameData;
    private final int runNumber;
    private final String playedAt;
    
    // Mappa immutabile dei risultati: Username -> Stato finale
    private final Map<String, PlayerGameState> playerResults;

    public PlayedGame(Game game, int run, Map<String, PlayerGameState> results) {
        this.gameData = game;
        this.runNumber = run;
        this.playedAt = LocalDateTime.now().toString();
        
        // CRITICO PER THREAD-SAFETY:
        // Creiamo una snapshot immutabile. Se anche qualcuno avesse ancora un riferimento
        // alla vecchia mappa 'results', le modifiche non devono toccare questo oggetto,
        // e nessuno deve poter fare .put() su questa mappa.
        this.playerResults = Collections.unmodifiableMap(new ConcurrentHashMap<>(results));
    }

    public Game getGameData() {
        return gameData;
    }

    public int getRunNumber() {
        return runNumber;
    }
    
    public Map<String, PlayerGameState> getPlayerResults() {
        return playerResults;
    }

    public PlayerGameState getPlayerState(String username) {
        if (playerResults == null) return null;
        return playerResults.get(username);
    }

    public String getPlayedAt() {
        return playedAt;
    }
}