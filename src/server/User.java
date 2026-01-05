package server;

public class User {
    private String username;
    private String password;
    private UserStats stats;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.stats = new UserStats();
    }

    public String getPassword() { return password; }
    public UserStats getStats() { return stats; }
    public String getUsername() { return username; }
}