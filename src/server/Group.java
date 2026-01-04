package server;
import java.util.List;

public class Group {
    String theme;
    List<String> words;
    // GSON usa i campi direttamente, ma i getter servono a te
    public String getTheme() { return theme; }
    public List<String> getWords() { return words; }
}
