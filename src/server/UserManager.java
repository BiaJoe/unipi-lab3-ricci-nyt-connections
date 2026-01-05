package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import server.models.User;
import server.models.UserStats;
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
    
    private final String usersFilePath = ServerConfig.USERS_FILE_PATH;

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

    // CORRETTO: Gestione aggiornamento senza perdere le statistiche
    public synchronized boolean updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        User u = users.get(oldName);
        if (u == null || !u.getPassword().equals(oldPsw)) return false;

        // 1. Cambio Username
        if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
            if (users.containsKey(newName)) return false; // Nuovo nome occupato
            
            // Creo nuovo utente
            User newUser = new User(newName, (newPsw != null && !newPsw.isEmpty() ? newPsw : oldPsw));
            
            // CRUCIALE: Trasferisco l'oggetto stats dal vecchio al nuovo
            // (Assumendo che User.getStats() ritorni il riferimento all'oggetto mutabile)
            newUser.setStats(u.getStats()); 
            
            users.remove(oldName);
            users.put(newName, newUser);
            
        } else if (newPsw != null && !newPsw.isEmpty()) {
            // 2. Cambio solo Password
            u.setPassword(newPsw);
        }
        
        saveUsers();
        return true;
    }

    public List<RankingEntry> getLeaderboard() {
        return users.values().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getStats().puzzlesWon, u1.getStats().puzzlesWon))
                .map(u -> new RankingEntry(0, u.getUsername(), u.getStats().puzzlesWon))
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
    
    public synchronized void updateStatsTimeOut(String username) {
        User u = users.get(username);
        if (u != null) {
            u.getStats().puzzlesPlayed++; 
            u.getStats().currentStreak = 0; 
            saveUsers();
        }
    }

    private void loadUsers() {
        if (usersFilePath == null) return;
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
        if (usersFilePath == null || users == null) return;
        try (FileWriter writer = new FileWriter(usersFilePath)) { gson.toJson(users, writer); } 
        catch (IOException e) { e.printStackTrace(); }
    }
}