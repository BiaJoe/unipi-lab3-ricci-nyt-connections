package server.models;

import java.util.List;

public class Game {
    private int gameId;
    private List<Group> groups;

    public int getGameId() { return gameId; }
    public List<Group> getGroups() { return groups; }

    public static class Group {
        private String theme;
        private List<String> words;

        public String getTheme() { return theme; }
        public List<String> getWords() { return words; }
    }
}
