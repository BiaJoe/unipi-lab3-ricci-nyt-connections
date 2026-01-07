package server.models;

import java.util.UUID;

public class User {
    // ID Univoco e immutabile
    private final String id;
    
    // Dati modificabili
    private String username;
    private String password;

    // --- DATI STATISTICHE ---
    private int puzzlesPlayed;
    private int puzzlesWon;
    private int currentStreak;
    private int maxStreak;
    private int totalScore; 
    private int[] winDistribution = new int[5];

    public User(String username, String password) {
        this.id = UUID.randomUUID().toString(); // Genera ID univoco automatico
        this.username = username;
        this.password = password;
    }

    public String getId() { return id; }
    
    // Account Logic
    public synchronized boolean checkPassword(String inputPsw) {
        return this.password.equals(inputPsw);
    }
    
    public synchronized void setPassword(String psw) { this.password = psw; }
    public synchronized void setUsername(String name) { this.username = name; }
    
    public synchronized String getUsername() { return username; }
    public synchronized String getPassword() { return password; }

    // Statistiche (Getters)
    public int getTotalScore() { return totalScore; }
    public int getPuzzlesPlayed() { return puzzlesPlayed; }
    public int getPuzzlesWon() { return puzzlesWon; }
    public int getCurrentStreak() { return currentStreak; }
    public int getMaxStreak() { return maxStreak; }
    public int[] getWinDistribution() { return winDistribution; }

    // --- LOGICA GIOCO (Thread-Safe) ---

    public synchronized void addWin(int errorsMade, int pointsEarned) {
        this.puzzlesPlayed++;
        this.puzzlesWon++;
        this.totalScore += pointsEarned;
        
        this.currentStreak++;
        if (this.currentStreak > this.maxStreak) this.maxStreak = this.currentStreak;

        if (errorsMade >= 0 && errorsMade < 4) this.winDistribution[errorsMade]++;
    }

    public synchronized void addLoss(int pointsEarned) {
        this.puzzlesPlayed++;
        this.totalScore += pointsEarned;
        this.currentStreak = 0;
        if (this.winDistribution.length > 4) this.winDistribution[4]++;
    }
}