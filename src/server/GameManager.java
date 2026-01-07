package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import server.models.Game;
import server.models.LiveStats;
import server.models.PlayedGame;
import server.models.PlayerGameState;
import server.ui.ServerLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static GameManager instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Stato Volatile (Partita corrente)
    private Game currentGame;
    private long gameStartTime;
    // Mappa: Username -> Stato di gioco di quel player per la partita corrente
    private ConcurrentHashMap<String, PlayerGameState> activePlayers;

    // Stato Persistente (Storico partite)
    // Mappa: GameID -> Ultima esecuzione (PlayedGame)
    private ConcurrentHashMap<Integer, PlayedGame> gamesHistory;

    private GameManager() {
        activePlayers = new ConcurrentHashMap<>();
        loadHistory();
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) instance = new GameManager();
        return instance;
    }

    // --- GESTIONE CICLO DI VITA PARTITA ---

    public synchronized void setCurrentGame(Game nextGame) {
        // Se c'è una partita in corso, archiviala prima di sovrascriverla
        if (this.currentGame != null) {
            archiveCurrentGame();
        }
        
        this.currentGame = nextGame;
        // Resetta i giocatori attivi per la nuova partita
        this.activePlayers.clear(); 
        this.resetGameTimer();
    }
    
    private void archiveCurrentGame() {
        if (currentGame == null) return;
        
        int gId = currentGame.getGameId();
        int nextRun = 1;
        
        // Se esiste già nello storico, incrementa il numero di esecuzione
        if (gamesHistory.containsKey(gId)) {
            nextRun = gamesHistory.get(gId).getRunNumber() + 1;
        }
        
        // Crea lo snapshot della partita giocata
        // Copiamo activePlayers per evitare modifiche concorrenti successive
        ConcurrentHashMap<String, PlayerGameState> playersSnapshot = new ConcurrentHashMap<>(activePlayers);
        PlayedGame archived = new PlayedGame(currentGame, nextRun, playersSnapshot);
        
        // AGGIORNAMENTO SOLO IN MEMORIA (Il PersistenceService farà il salvataggio su disco)
        gamesHistory.put(gId, archived);
        
        ServerLogger.info("Partita " + gId + " archiviata in memoria (Run #" + nextRun + ")");
    }

    // --- GETTER & UTILITY STATO ---

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

    /**
     * Recupera lo stato di un giocatore attivo. Se non esiste (nuovo join), lo crea vuoto.
     */
    public PlayerGameState getOrCreatePlayerState(String username) {
        if (username == null) return new PlayerGameState(); // Safe fallback
        return activePlayers.computeIfAbsent(username, k -> new PlayerGameState());
    }

    /**
     * Calcola le statistiche live della partita corrente per la dashboard.
     */
    public LiveStats calculateStats() {
        LiveStats stats = new LiveStats();
        for (PlayerGameState pState : activePlayers.values()) {
            if (pState.isFinished()) {
                stats.finished++;
                // Se ha finito e non ha raggiunto il limite errori -> ha vinto
                if (pState.getErrors() < ServerConfig.MAX_ERRORS) {
                    stats.won++;
                }
            } else {
                stats.active++;
            }
        }
        return stats;
    }

    /**
     * Recupera una partita dallo storico (per le richieste 'gameinfo <id>')
     */
    public PlayedGame getArchivedGame(int gameId) {
        return gamesHistory.get(gameId);
    }

    // --- PERSISTENZA ---

    private void loadHistory() {
        File file = new File(ServerConfig.HISTORY_FILE_PATH);
        if (!file.exists()) { 
            gamesHistory = new ConcurrentHashMap<>(); 
            return; 
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<Integer, PlayedGame>>(){}.getType();
            gamesHistory = gson.fromJson(reader, type);
            
            if (gamesHistory == null) {
                gamesHistory = new ConcurrentHashMap<>();
            }
            ServerLogger.info("Storico caricato: " + gamesHistory.size() + " partite.");
        } catch (IOException e) {
            ServerLogger.error("Errore caricamento storico: " + e.getMessage());
            gamesHistory = new ConcurrentHashMap<>();
        }
    }

    // Metodo pubblico chiamato dal PersistenceService
    public void saveData() {
        File file = new File(ServerConfig.HISTORY_FILE_PATH);
        
        // Assicura che la directory esista
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(gamesHistory, writer);
            // ServerLogger.info("Storico salvato su disco."); // Opzionale, può essere verboso
        } catch (IOException e) {
            ServerLogger.error("Errore salvataggio storico: " + e.getMessage());
        }
    }
}