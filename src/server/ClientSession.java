package server;

import java.util.HashSet;
import java.util.Set;

public class ClientSession {
    private StringBuilder bufferCheck;
    private String username;
    private boolean loggedIn;

    // --- PARTE GIOCO ---
    private int currentErrors;
    private Set<String> guessedThemes;
    
    // NUOVO: Flag per sapere se ha già concluso questa partita (Vinto/Perso)
    private boolean gameFinished; 

    public ClientSession() {
        this.bufferCheck = new StringBuilder();
        this.username = null;
        this.loggedIn = false;
        resetGameStatus();
    }

    public void resetGameStatus() {
        this.currentErrors = 0;
        this.guessedThemes = new HashSet<>();
        this.gameFinished = false; // Reset del flag
    }

    // --- GETTER E SETTER NUOVI ---
    public boolean isGameFinished() { return gameFinished; }
    public void setGameFinished(boolean finished) { this.gameFinished = finished; }

    // ... (Il resto dei metodi getBuffer, getUsername, addGuessedTheme rimangono uguali) ...
    public StringBuilder getBuffer() { return bufferCheck; }
    public void incrementErrors() { this.currentErrors++; }
    public int getErrors() { return currentErrors; }
    public void addGuessedTheme(String theme) { this.guessedThemes.add(theme); }
    public boolean isThemeGuessed(String theme) { return this.guessedThemes.contains(theme); }
    public int getScore() { return guessedThemes.size(); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
}