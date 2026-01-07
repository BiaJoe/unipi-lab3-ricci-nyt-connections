package server.models;

import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Set;

public class ClientSession {
    // Dati di connessione
    private boolean isLoggedIn;
    private String username;
    private InetAddress clientAddress; 
    private int udpPort;
    private StringBuilder buffer;

    private SelectionKey selectionKey;
    public void setSelectionKey(SelectionKey key) { this.selectionKey = key; }
    public SelectionKey getSelectionKey() { return selectionKey; }

    // RIFERIMENTO DIRETTO ALLO STATO DI GIOCO (Proxy pattern)
    private PlayerGameState state;

    public ClientSession() {
        this.buffer = new StringBuilder();
        this.isLoggedIn = false;
    }

    // Collega la sessione a uno stato di gioco (persistente nel GameManager)
    public void bindState(PlayerGameState state) {
        this.state = state;
    }

    // --- METODI MANCANTI AGGIUNTI ---

    public void resetGameStatus() {
        // Scollega lo stato corrente (usato al logout o cambio partita)
        this.state = null;
    }

    public Set<String> getGuessedThemes() {
        if (state != null) return state.getGuessedThemes();
        return Collections.emptySet(); // Ritorna set vuoto per sicurezza
    }

    // --- DELEGATE METHODS (Passano la chiamata allo stato) ---

    public int getErrors() {
        return (state != null) ? state.getErrors() : 0;
    }

    public void incrementErrors() {
        if (state != null) state.incrementErrors();
    }

    public int getScore() {
        return (state != null) ? state.getScore() : 0;
    }

    public boolean isThemeGuessed(String theme) {
        return state != null && state.getGuessedThemes().contains(theme);
    }

    public void addGuessedTheme(String theme) {
        if (state != null) state.addGuessedTheme(theme);
    }
    
    public boolean isGameFinished() {
        return state != null && state.isFinished();
    }

    public void setGameFinished(boolean finished) {
        if (state != null) state.setFinished(finished);
    }

    // --- GETTER & SETTER STANDARD ---
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public boolean isLoggedIn() { return isLoggedIn; }
    public void setLoggedIn(boolean loggedIn) { isLoggedIn = loggedIn; }
    
    public StringBuilder getBuffer() { return buffer; }
    
    public InetAddress getClientAddress() { return clientAddress; }
    public void setClientAddress(InetAddress clientAddress) { this.clientAddress = clientAddress; }
    
    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }
}