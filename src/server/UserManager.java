package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import server.models.User;
import server.ui.ServerLogger;
import utils.ServerResponse.RankingEntry;
import utils.ServerResponse.UserAccountInfo; 

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManager {
    private static UserManager instance;
    private ConcurrentHashMap<String, User> usersById; 
    private ConcurrentHashMap<String, String> usernameIndex;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String usersFilePath = ServerConfig.USERS_FILE_PATH;
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

    // --- ADMIN METHOD ---
    public List<UserAccountInfo> getUserListDebug() {
        List<UserAccountInfo> list = new ArrayList<>();
        for (User u : usersById.values()) {
            list.add(new UserAccountInfo(u.getUsername(), u.getPassword(), u.getTotalScore(), u.getPuzzlesPlayed(), u.getPuzzlesWon()));
        }
        return list;
    }

    // --- STANDARD METHODS ---
    public boolean login(String username, String password) {
        String id = usernameIndex.get(username);
        if (id == null) return false;
        User u = usersById.get(id);
        return u != null && u.checkPassword(password);
    }

    public boolean register(String username, String password) {
        synchronized (writeLock) {
            if (usernameIndex.containsKey(username)) return false;
            User newUser = new User(username, password);
            usersById.put(newUser.getId(), newUser);
            usernameIndex.put(username, newUser.getId());
            saveData(); 
            return true;
        }
    }

    public boolean updateCredentials(String oldName, String newName, String oldPsw, String newPsw) {
        synchronized (writeLock) {
            String id = usernameIndex.get(oldName);
            if (id == null) return false;
            User u = usersById.get(id);
            if (u == null || !u.checkPassword(oldPsw)) return false;

            if (newName != null && !newName.isEmpty() && !newName.equals(oldName)) {
                if (usernameIndex.containsKey(newName)) return false;
                usernameIndex.remove(oldName);
                usernameIndex.put(newName, id);
                u.setUsername(newName);
            }
            if (newPsw != null && !newPsw.isEmpty()) u.setPassword(newPsw);
            saveData();
            return true;
        }
    }

    public void updateGameResult(String username, int points, int errors, boolean won) {
        String id = usernameIndex.get(username);
        if (id != null) {
            usersById.computeIfPresent(id, (k, user) -> {
                if (won) {
                    // Aggiorna istogramma (0,1,2,3,4 errori)
                    user.addWin(errors, points); 
                } else {
                    // Non aggiorna istogramma
                    user.addLoss(points);
                }
                return user;
            });
            saveData(); 
        }
    }
    
    public void updateStatsTimeOut(String username) { 
        updateGameResult(username, 0, 0, false); 
    }
    
    public User getUser(String username) { String id = usernameIndex.get(username); return (id != null) ? usersById.get(id) : null; }

    public List<RankingEntry> getLeaderboard(Integer topK, String specificUser) {
        List<User> sortedUsers = usersById.values().stream()
                .sorted((u1, u2) -> Integer.compare(u2.getTotalScore(), u1.getTotalScore()))
                .collect(Collectors.toList());
        List<RankingEntry> result = new ArrayList<>();
        int rank = 1;
        for (User u : sortedUsers) {
            if (specificUser != null && !specificUser.isEmpty()) {
                if (u.getUsername().equals(specificUser)) {
                    result.add(new RankingEntry(rank, u.getUsername(), u.getTotalScore()));
                    return result;
                }
            } else {
                result.add(new RankingEntry(rank, u.getUsername(), u.getTotalScore()));
                if (topK != null && topK > 0 && result.size() >= topK) break;
            }
            rank++;
        }
        return result;
    }

    private void loadUsers() {
        File file = new File(usersFilePath);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            ConcurrentHashMap<String, User> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                this.usersById = loaded;
                this.usernameIndex.clear();
                for (User u : usersById.values()) this.usernameIndex.put(u.getUsername(), u.getId());
                ServerLogger.info("Caricati " + usersById.size() + " utenti.");
            }
        } catch (IOException e) { ServerLogger.error("Errore caricamento utenti: " + e.getMessage()); }
    }

    public void saveData() {
        if (usersById == null) return;
        File file = new File(usersFilePath);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) { gson.toJson(usersById, writer); } 
        catch (IOException e) { ServerLogger.error("Errore salvataggio utenti: " + e.getMessage()); }
    }
}