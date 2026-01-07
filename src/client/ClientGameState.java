package client;

import java.util.ArrayList;
import java.util.List;

public class ClientGameState {
    private static volatile int timeLeft = 0;
    private static volatile int errors = 0;
    private static volatile boolean isGameActive = false;
    
    // Memorizziamo la griglia per disegnarla nell'HUD
    private static volatile List<String> currentWords = new ArrayList<>();

    // Aggiornamento completo dal Server
    public static void update(int time, int err, List<String> words, boolean active) {
        timeLeft = time;
        errors = err;
        if (words != null) currentWords = new ArrayList<>(words);
        isGameActive = active;
    }

    // Aggiornamento parziale (es. errore commesso)
    public static void updateStats(int err) {
        errors = err;
    }

    // Countdown locale (finto)
    public static void tick() {
        if (timeLeft > 0 && isGameActive) {
            timeLeft--;
        } else if (timeLeft <= 0) {
            isGameActive = false; // Ferma il counter a 0
        }
    }

    public static int getTimeLeft() { return timeLeft; }
    public static int getErrors() { return errors; }
    public static boolean isActive() { return isGameActive; }
    public static List<String> getCurrentWords() { return currentWords; }
}