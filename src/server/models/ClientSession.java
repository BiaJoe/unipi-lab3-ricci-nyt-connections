package server.models;

import java.util.HashSet;
import java.util.Set;

public class ClientSession {
    private String username;
    private boolean isLoggedIn;
    private StringBuilder buffer;
    
    // Manteniamo questi campi per comodità di accesso rapido durante la connessione attiva,
    // ma verranno sincronizzati col GameManager.
    private Set<String> guessedThemes;
    private int errors;
    private boolean gameFinished;
    
    // Rimosso shuffledWords (ora è nel GameManager)

    public ClientSession() {
        this.buffer = new StringBuilder();
        resetGameStatus();
    }

    public void resetGameStatus() {
        this.guessedThemes = new HashSet<>();
        this.errors = 0;
        this.gameFinished = false;
    }

    // Metodo per "caricare" i dati dal salvataggio del GameManager dentro la sessione attiva
    public void restoreFromState(PlayerGameState state) {
        this.guessedThemes = new HashSet<>(state.getGuessedThemes());
        this.errors = state.getErrors();
        this.gameFinished = state.isFinished();
    }

    public int getScore() { return guessedThemes.size(); }
    public void incrementErrors() { this.errors++; }
    public void addGuessedTheme(String theme) { guessedThemes.add(theme); }
    public boolean isThemeGuessed(String theme) { return guessedThemes.contains(theme); }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    public StringBuilder getBuffer() { return buffer; }
    public Set<String> getGuessedThemes() { return guessedThemes; }
    public int getErrors() { return errors; }
    public boolean isGameFinished() { return gameFinished; }
    public void setGameFinished(boolean gameFinished) { this.gameFinished = gameFinished; }
}