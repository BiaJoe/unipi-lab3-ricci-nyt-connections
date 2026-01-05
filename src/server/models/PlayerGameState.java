package server.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerGameState {
    // Dati di progresso nella partita corrente
    private List<String> shuffledWords;
    private Set<String> guessedThemes;
    private int errors;
    private boolean isFinished;

    public PlayerGameState() {
        this.guessedThemes = new HashSet<>();
        this.shuffledWords = new ArrayList<>();
        this.errors = 0;
        this.isFinished = false;
    }

    public List<String> getShuffledWords() { return shuffledWords; }
    public void setShuffledWords(List<String> shuffledWords) { this.shuffledWords = shuffledWords; }

    public Set<String> getGuessedThemes() { return guessedThemes; }
    public void addGuessedTheme(String theme) { guessedThemes.add(theme); }

    public int getErrors() { return errors; }
    public void incrementErrors() { this.errors++; }
    
    public boolean isFinished() { return isFinished; }
    public void setFinished(boolean finished) { isFinished = finished; }
    
    public int getScore() { return guessedThemes.size(); }
}