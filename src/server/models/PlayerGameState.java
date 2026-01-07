package server.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerGameState {
    private List<String> shuffledWords = new ArrayList<>();
    private Set<String> guessedThemes = new HashSet<>();
    private int errors = 0;
    private boolean isFinished = false;

    public PlayerGameState() {}

    public List<String> getShuffledWords() { return shuffledWords; }
    public void setShuffledWords(List<String> list) { this.shuffledWords = list; }

    public Set<String> getGuessedThemes() { return guessedThemes; }
    public void addGuessedTheme(String theme) { guessedThemes.add(theme); }
    public boolean isThemeGuessed(String theme) { return guessedThemes.contains(theme); }

    public int getErrors() { return errors; }
    public void incrementErrors() { this.errors++; }
    
    public boolean isFinished() { return isFinished; }
    public void setFinished(boolean finished) { this.isFinished = finished; }
    
    public int getScore() { return guessedThemes.size(); }
}