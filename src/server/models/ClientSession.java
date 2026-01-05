package server.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientSession {
    private String username;
    private boolean isLoggedIn;
    private StringBuilder buffer;
    
    // Stato Partita
    private Set<String> guessedThemes;
    private int errors;
    private boolean gameFinished;
    
    // NUOVO: Ricorda l'ordine delle parole per questa sessione
    private List<String> personalShuffledWords; 

    public ClientSession() {
        this.buffer = new StringBuilder();
        resetGameStatus();
    }

    public void resetGameStatus() {
        this.guessedThemes = new HashSet<>();
        this.errors = 0;
        this.gameFinished = false;
        this.personalShuffledWords = null; // Al reset, cancelliamo lo shuffle vecchio
    }

    public int getScore() { return guessedThemes.size(); }
    public void incrementErrors() { this.errors++; }
    public void addGuessedTheme(String theme) { guessedThemes.add(theme); }
    public boolean isThemeGuessed(String theme) { return guessedThemes.contains(theme); }

    // Getters & Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    
    public StringBuilder getBuffer() { return buffer; }
    
    public Set<String> getGuessedThemes() { return guessedThemes; }
    public int getErrors() { return errors; }
    
    public boolean isGameFinished() { return gameFinished; }
    public void setGameFinished(boolean gameFinished) { this.gameFinished = gameFinished; }

    // NUOVI GETTER/SETTER per lo shuffle persistente
    public List<String> getPersonalShuffledWords() { return personalShuffledWords; }
    public void setPersonalShuffledWords(List<String> words) { this.personalShuffledWords = words; }
}