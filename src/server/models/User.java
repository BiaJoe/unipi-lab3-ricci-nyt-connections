package server.models;

public class User {
    private String username;
    private String password;
    private UserStats stats;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.stats = new UserStats();
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public UserStats getStats() { return stats; }
    public void setStats(UserStats stats) { this.stats = stats; }
}