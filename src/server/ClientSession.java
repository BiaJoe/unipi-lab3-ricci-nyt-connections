package server;

public class ClientSession {
    private String username;
    private boolean loggedIn;
    // Buffer per accumulare pezzi di JSON se arrivano spezzati (tipico di NIO)
    private StringBuilder bufferCheck; 

    public ClientSession() {
        this.username = null;
        this.loggedIn = false;
        this.bufferCheck = new StringBuilder();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
    
    public StringBuilder getBuffer() { return bufferCheck; }
}