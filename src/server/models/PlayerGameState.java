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
        // Calcolo punteggio: (Gruppi * 6) - (Errori * 4)
        // Se ho vinto con 3 gruppi, il sistema ne conta 3.
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

    // NUOVI GETTER/SETTER PER WON
    public boolean hasWon() { return won; }
    public void setWon(boolean won) { this.won = won; }
}