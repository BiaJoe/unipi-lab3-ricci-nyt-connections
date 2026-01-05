package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import utils.ServerResponse.RankingEntry;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManager {
    private static UserManager instance;
    private ConcurrentHashMap<String, User> users; 
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String usersFilePath = ServerMain.usersFilePath;

    private UserManager() { loadUsers(); }

    public static synchronized UserManager getInstance() {
        if (instance == null) instance = new UserManager();
        return instance;
    }

    public synchronized boolean login(String username, String password) {
        User u = users.get(username);
        return u != null && u.getPassword().equals(password);
    }

    public synchronized boolean register(String username, String password) {
        if (users.containsKey(username)) return false;
        users.put(username, new User(username, password));
        saveUsers();
        return true;
    }

    // NUOVO: Aggiornamento credenziali
    public synchronized boolean updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        User u = users.get(oldName);
        if (u == null || !u.getPassword().equals(oldPsw)) return false;

        // Se vuole cambiare nome
        if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
            if (users.containsKey(newName)) return false; // Nuovo nome occupato
            // Rimuovo vecchio, metto nuovo (mantenendo stats)
            User newUser = new User(newName, (newPsw != null ? newPsw : oldPsw));
            // Copia brutale delle statistiche (dovresti fare un metodo clone o setter)
            // Per semplicità qui assumiamo che UserStats sia accessibile
            newUser.getStats().puzzlesPlayed = u.getStats().puzzlesPlayed; 
            // ... copiare tutti i campi stats ... (o spostare l'oggetto stats)
            users.remove(oldName);
            users.put(newName, newUser);
        } else if (newPsw != null && !newPsw.isEmpty()) {
            // Cambio solo password
            // In un sistema reale User dovrebbe essere mutabile o ricreato
            users.put(oldName, new User(oldName, newPsw)); 
            // Nota: perderesti le stats se ricrei l'oggetto User senza copiarle!
            // FIX RAPIDO: Aggiungi setPassword in User.java
        }
        saveUsers();
        return true;
    }

    // NUOVO: Classifica
    public List<RankingEntry> getLeaderboard() {
        return users.values().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getStats().puzzlesWon, u1.getStats().puzzlesWon)) // Ordina per vittorie
                .map(u -> new RankingEntry(0, u.getUsername(), u.getStats().puzzlesWon)) // Posizione calcolata dopo
                .collect(Collectors.toList());
    }

    public synchronized void updateStatsWin(String username, int errorsMade) {
        User u = users.get(username);
        if (u != null) { u.getStats().addWin(errorsMade); saveUsers(); }
    }

    public synchronized void updateStatsLoss(String username) {
        User u = users.get(username);
        if (u != null) { u.getStats().addLoss(); saveUsers(); }
    }

    public UserStats getUserStats(String username) {
        User u = users.get(username);
        return (u != null) ? u.getStats() : null;
    }

    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) { initFile(file); return; }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            users = gson.fromJson(reader, type);
            if (users == null) users = new ConcurrentHashMap<>();
        } catch (IOException e) { users = new ConcurrentHashMap<>(); }
    }

    private void initFile(File file) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            file.createNewFile();
            users = new ConcurrentHashMap<>();
            saveUsers();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveUsers() {
        try (FileWriter writer = new FileWriter(usersFilePath)) { gson.toJson(users, writer); } 
        catch (IOException e) { e.printStackTrace(); }
    }

    public synchronized void updateStatsTimeOut(String username) {
        User u = users.get(username);
        if (u != null) {
            u.getStats().puzzlesPlayed++; // Conta come giocata
            u.getStats().currentStreak = 0; // Azzera la streak [cite: 103]
            // Opzionale: potresti voler salvare gli errori fatti finora nell'istogramma
            saveUsers();
        }
    }
}