package server.models;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class ClientSession {
    private String username;
    private boolean isLoggedIn;
    private StringBuilder buffer;
    
    // --- NUOVI CAMPI PER UDP ---
    private InetAddress clientAddress; // IP del client
    private int udpPort;             // Porta UDP del client

    // Manteniamo questi campi per comodità di accesso rapido durante la connessione attiva,
    // ma verranno sincronizzati col GameManager.
    private Set<String> guessedThemes;
    private int errors;
    private boolean gameFinished;
    
    public ClientSession() {
        this.buffer = new StringBuilder();
        resetGameStatus();
    }

    public void resetGameStatus() {
        this.guessedThemes = new HashSet<>();
        this.errors = 0;
        this.gameFinished = false;
    }

    public void restoreFromState(PlayerGameState state) {
        this.guessedThemes = new HashSet<>(state.getGuessedThemes());
        this.errors = state.getErrors();
        this.gameFinished = state.isFinished();
    }

    // --- GETTER & SETTER ---
    public InetAddress getClientAddress() { return clientAddress; }
    public void setClientAddress(InetAddress clientAddress) { this.clientAddress = clientAddress; }

    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }

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