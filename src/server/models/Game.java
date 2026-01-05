package server.models;
import java.util.List;

public class Game {
    int gameId;
    List<Group> groups;
    
    public int getGameId() { return gameId; }
    public List<Group> getGroups() { return groups; }
}