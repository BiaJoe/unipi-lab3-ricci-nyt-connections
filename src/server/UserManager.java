package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import server.models.User;
import server.models.UserStats;
import server.ui.ServerLogger;
import utils.ServerResponse.RankingEntry;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManager {
    private static UserManager instance;
    
    // Mappa thread-safe per definizione. 
    // Non serve 'synchronized' sui metodi di lettura semplici.
    private ConcurrentHashMap<String, User> users; 
    
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String usersFilePath = ServerConfig.USERS_FILE_PATH;

    // Lock specifico per operazioni che modificano la struttura delle chiavi (es. cambio username)
    private final Object structuralLock = new Object();

    private UserManager() { 
        loadUsers(); 
    }

    // Singleton Thread-Safe classico
    public static synchronized UserManager getInstance() {
        if (instance == null) instance = new UserManager();
        return instance;
    }

    // --- LOGICA UTENTI ---

    public boolean login(String username, String password) {
        User u = users.get(username);
        // Controllo null-safe
        return u != null && u.getPassword().equals(password);
    }

    public boolean register(String username, String password) {
        // putIfAbsent è atomico: se la chiave esiste non fa nulla e ritorna il valore esistente.
        // Se non esiste, inserisce e ritorna null.
        // Molto più efficiente di containsKey + put in un blocco synchronized.
        return users.putIfAbsent(username, new User(username, password)) == null;
    }

    public boolean updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        // Operazione complessa: richiede consistenza tra rimozione e inserimento.
        // Usiamo un lock specifico per non bloccare tutto il UserManager per operazioni banali.
        synchronized (structuralLock) {
            User u = users.get(oldName);
            
            // Verifica credenziali vecchie
            if (u == null || !u.getPassword().equals(oldPsw)) return false;

            // CASO 1: Cambio Username (Critico)
            if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
                if (users.containsKey(newName)) return false; // Nuovo nome occupato
                
                // Creiamo nuovo utente mantenendo le statistiche
                User newUser = new User(newName, (newPsw != null && !newPsw.isEmpty() ? newPsw : oldPsw));
                newUser.setStats(u.getStats());
                
                // Swap atomico (protetto dal structuralLock)
                users.remove(oldName);
                users.put(newName, newUser);
                return true;
            } 
            
            // CASO 2: Solo Cambio Password
            if (newPsw != null && !newPsw.isEmpty()) {
                u.setPassword(newPsw);
                return true;
            }
        }
        return false;
    }

    /**
     * Aggiorna le statistiche a fine partita.
     * Metodo Thread-Safe che usa computeIfPresent per garantire atomicità sull'aggiornamento.
     * * @param username Nome utente
     * @param points Punti calcolati (es. 18, 14, -4, etc.)
     * @param won True se la partita è vinta, False se persa/tempo scaduto
     */
    public void updateGameResult(String username, int points, boolean won) {
        users.computeIfPresent(username, (key, user) -> {
            // Sincronizziamo sul singolo oggetto utente per evitare race conditions
            // se lo stesso utente (assurdo ma possibile) finisse due partite simultaneamente.
            synchronized (user) {
                UserStats stats = user.getStats();
                
                stats.puzzlesPlayed++;
                stats.totalScore += points; // Aggiorna punteggio totale (Rank)
                
                if (won) {
                    stats.puzzlesWon++;
                    stats.currentStreak++;
                    if (stats.currentStreak > stats.maxStreak) {
                        stats.maxStreak = stats.currentStreak;
                    }
                    // Aggiungi statistiche vittoria all'istogramma (assumendo che points/6 sia indicativo o gestito altrove)
                    // Per semplicità qui aggiorniamo solo i contatori base, l'istogramma richiederebbe gli errori specifici
                } else {
                    stats.currentStreak = 0;
                    stats.addLoss(); // Incrementa contatore sconfitte/distribuzione
                }
            }
            return user;
        });
    }
    
    // Gestione specifica per il TimeOut (se non gestito da updateGameResult generico)
    public void updateStatsTimeOut(String username) {
        // Applichiamo la penalità base o 0 punti? 
        // Se l'utente non ha fatto nulla prende 0. Se ha fatto errori sono già calcolati.
        // Qui assumiamo un reset della streak e +1 giocata.
        updateGameResult(username, 0, false);
    }

    public UserStats getUserStats(String username) {
        User u = users.get(username);
        // Ritorniamo una copia o l'oggetto diretto? 
        // Per performance ritorniamo l'oggetto, ma attenzione a modifiche concorrenti esterne.
        return (u != null) ? u.getStats() : null;
    }

    /**
     * Genera la classifica basata sul PUNTEGGIO TOTALE (totalScore).
     * @return Lista ordinata
     */
    public List<RankingEntry> getLeaderboard() {
        return users.values().stream()
                // Ordina per Punteggio Totale decrescente
                .sorted((u1, u2) -> Integer.compare(u2.getStats().totalScore, u1.getStats().totalScore))
                .map(u -> new RankingEntry(0, u.getUsername(), u.getStats().totalScore))
                .collect(Collectors.toList());
    }

    // --- PERSISTENZA ---

    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) {
            users = new ConcurrentHashMap<>();
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            ConcurrentHashMap<String, User> loaded = gson.fromJson(reader, type);
            
            // Controllo robustezza
            this.users = (loaded != null) ? loaded : new ConcurrentHashMap<>();
            ServerLogger.info("Caricati " + users.size() + " utenti.");
            
        } catch (IOException e) { 
            users = new ConcurrentHashMap<>(); 
            ServerLogger.error("Errore caricamento utenti: " + e.getMessage());
        }
    }

    public void saveData() {
        if (usersFilePath == null || users == null) return;
        
        // Salvataggio snapshot
        try (FileWriter writer = new FileWriter(usersFilePath)) { 
            gson.toJson(users, writer); 
        } catch (IOException e) { 
            ServerLogger.error("Errore salvataggio utenti: " + e.getMessage()); 
        }
    }
    
    // Metodo helper per test/debug
    public void reset() {
        users.clear();
    }
}