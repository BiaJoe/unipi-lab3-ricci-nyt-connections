package server.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import server.ServerConfig;
import server.models.Game;
import server.models.GameMatch; 
import server.models.PlayerGameState;
import server.ui.ServerLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 */
public class GameManager {
    private static GameManager instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // PARTITA CORRENTE 
    private GameMatch currentMatch;

    // STORICO 
    private ConcurrentHashMap<Integer, GameMatch> matchHistory;

    private GameManager() {
        matchHistory = new ConcurrentHashMap<>();
        loadHistory();
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) instance = new GameManager();
        return instance;
    }

    // CICLO DI VITA

    public synchronized void setCurrentGame(Game nextGameDef) {
        // 1. Archivia la partita precedente se esiste
        if (this.currentMatch != null) {
            matchHistory.put(currentMatch.getGameId(), currentMatch);
            ServerLogger.info("Archivita partita ID " + currentMatch.getGameId() + " (Run " + currentMatch.getRunNumber() + ")");
        }
        
        // 2. Calcola il prossimo run number
        int nextRun = 1;
        if (matchHistory.containsKey(nextGameDef.getGameId())) {
            nextRun = matchHistory.get(nextGameDef.getGameId()).getRunNumber() + 1;
        }

        // 3. Crea la nuova partita (Tutto lo stato è incapsulato)
        this.currentMatch = new GameMatch(nextGameDef, nextRun);
    }


    // Ritorna il Match corrente (dove i player giocano ora)
    public synchronized GameMatch getCurrentMatch() { 
        return currentMatch; 
    }
    
    // Helper per abbreviare il codice negli Handler
    public Game getCurrentGameDefinition() {
        return (currentMatch != null) ? currentMatch.getGameData() : null;
    }
    
    public int getTimeLeft() {
        return (currentMatch != null) ? currentMatch.getTimeLeft() : 0;
    }
    
    // Delega al Match corrente
    public PlayerGameState getOrCreatePlayerState(String username) {
        if (currentMatch == null) return new PlayerGameState(); // Fallback safe
        return currentMatch.getOrCreatePlayerState(username);
    }

    // Recupera dallo storico o ritorna il corrente se l'ID coincide
    public GameMatch getGameMatchById(int gameId) {
        // Se chiedono la partita attiva
        if (currentMatch != null && currentMatch.getGameId() == gameId) {
            return currentMatch;
        }
        // Altrimenti cerco nello storico
        return matchHistory.get(gameId);
    }

    // PERSISTENZA

    private void loadHistory() {
        File file = new File(ServerConfig.HISTORY_FILE_PATH);
        if (!file.exists()) { matchHistory = new ConcurrentHashMap<>(); return; }
        try (FileReader reader = new FileReader(file)) {
            // Leggiamo direttamente GameMatch!
            Type type = new TypeToken<ConcurrentHashMap<Integer, GameMatch>>(){}.getType();
            matchHistory = gson.fromJson(reader, type);
            if (matchHistory == null) matchHistory = new ConcurrentHashMap<>();
            ServerLogger.info("Storico caricato: " + matchHistory.size() + " partite.");
        } catch (IOException e) {
            ServerLogger.error("Errore caricamento storico: " + e.getMessage());
            matchHistory = new ConcurrentHashMap<>();
        }
    }

    public void saveData() {
        File file = new File(ServerConfig.HISTORY_FILE_PATH);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        
        try (FileWriter writer = new FileWriter(file)) {
            // Salviamo lo stato attuale dello storico
            gson.toJson(matchHistory, writer);
        } catch (IOException e) {
            ServerLogger.error("Errore salvataggio storico: " + e.getMessage());
        }
    }
}