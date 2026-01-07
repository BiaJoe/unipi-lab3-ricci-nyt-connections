package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import server.models.User;
import server.ui.ServerLogger;
import utils.ServerResponse.RankingEntry;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManager {
    private static UserManager instance;
    
    // MAPPA 1 (Storage): ID -> User
    private ConcurrentHashMap<String, User> usersById; 
    
    // MAPPA 2 (Indice): Username -> ID (Per login O(1))
    private ConcurrentHashMap<String, String> usernameIndex;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String usersFilePath = ServerConfig.USERS_FILE_PATH;
    
    // Lock per operazioni strutturali (Register, UpdateUsername)
    // Serve a garantire che due persone non prendano lo stesso nome contemporaneamente
    private final Object writeLock = new Object();

    private UserManager() { 
        usersById = new ConcurrentHashMap<>();
        usernameIndex = new ConcurrentHashMap<>();
        loadUsers(); 
    }

    public static synchronized UserManager getInstance() {
        if (instance == null) instance = new UserManager();
        return instance;
    }

    // --- LOGICA ACCOUNT (Velocissima O(1)) ---

    public boolean login(String username, String password) {
        // 1. Cerca ID nell'indice
        String id = usernameIndex.get(username);
        if (id == null) return false;

        // 2. Cerca Utente nella mappa dati
        User u = usersById.get(id);
        return u != null && u.checkPassword(password);
    }

    public boolean register(String username, String password) {
        synchronized (writeLock) {
            // Controllo veloce sull'indice
            if (usernameIndex.containsKey(username)) return false;

            User newUser = new User(username, password);
            
            // Inserimento atomico (concettualmente)
            usersById.put(newUser.getId(), newUser);
            usernameIndex.put(username, newUser.getId());
            
            return true;
        }
    }

    public boolean updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        synchronized (writeLock) {
            // 1. Recupero Utente tramite Indice
            String id = usernameIndex.get(oldName);
            if (id == null) return false;

            User u = usersById.get(id);
            if (u == null || !u.checkPassword(oldPsw)) return false;

            // 2. Cambio Username (Parte delicata)
            if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
                // Controllo se il nuovo nome è libero
                if (usernameIndex.containsKey(newName)) return false;

                // AGGIORNAMENTO INDICE:
                // Rimuovo la vecchia "etichetta"
                usernameIndex.remove(oldName);
                // Aggiungo la nuova "etichetta" che punta allo STESSO ID
                usernameIndex.put(newName, id);
                
                // Aggiorno il campo interno dell'oggetto User
                u.setUsername(newName);
            }

            // 3. Cambio Password (Banale)
            if (newPsw != null && !newPsw.isEmpty()) {
                u.setPassword(newPsw);
            }
            
            return true;
        }
    }

    // --- LOGICA GIOCO ---

    public void updateGameResult(String username, int points, int errors, boolean won) {
        // Look-up veloce O(1)
        String id = usernameIndex.get(username);
        if (id != null) {
            // computeIfPresent è atomico sulla mappa ID
            usersById.computeIfPresent(id, (k, user) -> {
                if (won) user.addWin(errors, points);
                else user.addLoss(points);
                return user;
            });
        }
    }
    
    public void updateStatsTimeOut(String username) {
        updateGameResult(username, 0, 0, false);
    }

    public User getUser(String username) {
        String id = usernameIndex.get(username);
        return (id != null) ? usersById.get(id) : null;
    }

    public List<RankingEntry> getLeaderboard() {
        return usersById.values().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getTotalScore(), u1.getTotalScore()))
                .map(u -> new RankingEntry(0, u.getUsername(), u.getTotalScore()))
                .collect(Collectors.toList());
    }

    // --- PERSISTENZA ---

    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            // Carichiamo solo la mappa principale (ID -> User)
            Type type = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            ConcurrentHashMap<String, User> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                this.usersById = loaded;
                
                // *** RICOSTRUZIONE DELL'INDICE ***
                // Iteriamo sugli utenti caricati e ripopoliamo la mappa Username -> ID
                this.usernameIndex.clear();
                for (User u : usersById.values()) {
                    this.usernameIndex.put(u.getUsername(), u.getId());
                }
                ServerLogger.info("Caricati " + usersById.size() + " utenti (Indice ricostruito).");
            }
        } catch (IOException e) {
            ServerLogger.error("Errore caricamento utenti: " + e.getMessage());
        }
    }

    public void saveData() {
        if (usersById == null) return;
        try (FileWriter writer = new FileWriter(usersFilePath)) { 
            // Salviamo solo la mappa vera (ID -> User). L'indice si rifà da solo.
            gson.toJson(usersById, writer); 
        } catch (IOException e) { 
            ServerLogger.error("Errore salvataggio utenti: " + e.getMessage()); 
        }
    }
}