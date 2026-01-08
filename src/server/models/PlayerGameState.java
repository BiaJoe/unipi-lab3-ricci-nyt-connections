package server.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerGameState {
    private final Set<String> guessedThemes = new HashSet<>();
    private List<String> shuffledWords = new ArrayList<>();
    private int errors = 0;
    private boolean finished = false;
    private boolean won = false; 

    // --- LOGICA DI GIOCO ---

    public void addGuessedTheme(String theme) {
        guessedThemes.add(theme);
    }
    
    public boolean isThemeGuessed(String theme) {
        return guessedThemes.contains(theme);
    }

    public void incrementErrors() {
        errors++;
    }

    public int getScore() {
        return (guessedThemes.size() * 6) - (errors * 4);
    }

    public int getGroupsFoundCount() {
        return guessedThemes.size();
    }

    // --- GETTERS & SETTERS ---
    public Set<String> getGuessedThemes() { return guessedThemes; }
    public int getErrors() { return errors; }
    
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    
    public List<String> getShuffledWords() { return shuffledWords; }
    public void setShuffledWords(List<String> words) { this.shuffledWords = words; }

    // --- GESTIONE VITTORIA ---
    // Usato da GameMatch per le statistiche
    public boolean hasWon() { return won; }
    
    // Usato da GameHandler per decretare la vittoria
    public void setWon(boolean won) { this.won = won; } 
}