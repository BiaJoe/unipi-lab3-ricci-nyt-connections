package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class ConsoleUI {
    
    // --- FIX: ORA SONO PUBLIC ---
    public static final String SAVE_CURSOR = "\0337";
    public static final String RESTORE_CURSOR = "\0338";
    public static final String CYAN = "\033[0;36m";
    public static final String RED = "\033[0;31m";
    public static final String GREEN = "\033[0;32m";
    public static final String YELLOW = "\033[0;33m";
    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";

    // Configurazione posizione HUD (colonna di partenza)
    private static int HUD_COL = 80; 

    public static void setHudColumn(int col) {
        HUD_COL = col;
    }

    // --- LOGICA DI STAMPA A SINISTRA (SCROLLING LOG) ---
    public static void printMessage(String msg) {
        // Cancelliamo la riga corrente per evitare conflitti con il prompt ">"
        System.out.print("\r\033[K"); 
        System.out.println(msg);
        repaintHud(); 
    }

    public static void printError(String msg) {
        printMessage(RED + "[ERROR] " + msg + RESET);
    }

    public static void printServerMsg(String msg) {
        printMessage(CYAN + "[SERVER] " + msg + RESET);
    }

    // --- LOGICA DI STAMPA A DESTRA (HUD FISSO) ---
    public static void repaintHud() {
        if (!ClientGameState.isActive()) return;

        List<String> words = ClientGameState.getCurrentWords();
        int time = ClientGameState.getTimeLeft();
        int errors = ClientGameState.getErrors();

        String timeStr = String.format("%02d:%02d", time / 60, time % 60);
        String errStr = formatErrors(errors);

        System.out.print(SAVE_CURSOR);

        printAt(1, HUD_COL, CYAN + "+---------------------------+");
        printAt(2, HUD_COL, "| " + YELLOW + "TEMPO: " + RESET + timeStr + pad(16 - timeStr.length()) + CYAN + "|");
        printAt(3, HUD_COL, "| " + YELLOW + "ERRORI: " + errStr + pad(15 - 8) + CYAN + "|");
        printAt(4, HUD_COL, "+---------------------------+");
        
        if (words != null && !words.isEmpty()) {
            int row = 6;
            printAt(row++, HUD_COL, BOLD + "PARTITA CORRENTE:" + RESET);
            
            for (int i = 0; i < words.size(); i++) {
                String w = words.get(i);
                if (w.isEmpty()) w = "---"; 
                
                String formattedWord = (w.equals("---")) ? GREEN + w + RESET : w;
                printAt(row++, HUD_COL, String.format(" %-20s", formattedWord));
            }
        }

        System.out.print(RESTORE_CURSOR);
    }

    private static void printAt(int row, int col, String text) {
        System.out.print("\033[" + row + ";" + col + "H" + text + RESET);
    }

    private static String formatErrors(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < count) sb.append(RED + "X " + RESET);
            else sb.append(GREEN + "O " + RESET);
        }
        return sb.toString();
    }
    
    private static String pad(int n) {
        if (n <= 0) return "";
        return String.format("%" + n + "s", "");
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void printFile(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                printMessage(line); 
            }
        } catch (IOException e) {
            printError("File non trovato: " + filename);
        }
    }
}