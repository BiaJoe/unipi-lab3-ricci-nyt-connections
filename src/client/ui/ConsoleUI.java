package client.ui;

import client.logic.CommandProcessor;
import client.logic.Command;
import utils.ServerResponse;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ConsoleUI implements ClientRenderer {
    private final String trophyFile;
    
    // ANSI Colors
    private static final String RESET = "\033[0m";
    private static final String CYAN = "\033[0;36m";
    private static final String RED = "\033[0;31m";
    private static final String GREEN = "\033[0;32m";
    private static final String YELLOW = "\033[0;33m";
    private static final String PURPLE = "\033[0;35m";

    public ConsoleUI(String ignoredHelp, String trophyFile) {
        this.trophyFile = trophyFile;
    }

    @Override
    public void init() {
        System.out.println(CYAN + "###### The New New York Times Connections ######" + RESET);
    }

    public void runInputLoop(CommandProcessor processor) {
        processor.processInput("/help");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) { printPrompt(); continue; }
                if (!processor.processInput(line)) break;
            }
        }
    }

    // --- IMPLEMENTAZIONE VISUALIZZAZIONE ---

    @Override
    public void showHelp(Collection<Command> commands) {
        System.out.println("\n" + CYAN + "=== COMANDI DISPONIBILI ===" + RESET);
        System.out.printf("  %-10s %-35s %s\n", "ALIAS", "COMANDO", "DESCRIZIONE");
        System.out.println("  ----------------------------------------------------------------");
        for (Command cmd : commands) {
            String aliasDisplay = (cmd.alias == null || cmd.alias.isEmpty()) ? "" : "/" + cmd.alias;
            System.out.printf("  " + YELLOW + "%-10s" + RESET + " %-35s %s\n", aliasDisplay, cmd.getUsage(), cmd.description);
        }
        printPrompt();
    }

    @Override
    public void showMessage(String msg) {
        System.out.println(GREEN + "[INFO] " + msg + RESET);
        printPrompt();
    }

    @Override
    public void showError(String err) {
        System.out.println(RED + "[!] " + err + RESET);
        printPrompt();
    }

    @Override
    public void showGameInfo(ServerResponse.GameInfoData info) {
        System.out.println("\n" + YELLOW + "--- PARTITA #" + info.gameId + " ---" + RESET);
        
        boolean victory = (info.correctGroups != null && info.correctGroups.size() == 4);
        
        // Se la partita è finita (o tempo scaduto)
        if (Boolean.TRUE.equals(info.isFinished) || info.timeLeft <= 0) {
            String esito;
            if (victory) {
                esito = GREEN + "VITTORIA!" + RESET;
            } else {
                esito = RED + "PERSO (Tempo scaduto o troppi errori)" + RESET;
            }
            
            System.out.println(CYAN + "Stato: " + esito + " | Punti Finali: " + info.currentScore);
        } else {
            // Partita in corso
            System.out.printf("Tempo: %ds | Punti: %d | Errori: %d/4\n", info.timeLeft, info.currentScore, info.mistakes);
        }
        // ---------------------------

        if (info.words != null && !info.words.isEmpty()) {
            printGrid(info.words, info.correctGroups);
        }

        // Mostra soluzione se presente (di solito quando finisce)
        if (Boolean.TRUE.equals(info.isFinished) && info.solution != null) {
            System.out.println("\n" + PURPLE + "=== SOLUZIONE ===" + RESET);
            for (var g : info.solution) System.out.println(" * " + g.theme + ": " + g.words);
        }

        // Mostra classifica partita
        if (info.playerResults != null && !info.playerResults.isEmpty()) {
            System.out.println("\n" + PURPLE + "=== PARTECIPANTI ===" + RESET);
            for (var p : info.playerResults) {
                String esito = p.won ? (GREEN + "VITTORIA" + RESET) : (RED + "PERSO" + RESET);
                System.out.printf(" - %-15s: %s (Punti: %d)\n", p.username, esito, p.score);
            }
        }
        printPrompt();
    }

    @Override
    public void showSubmitResult(ServerResponse.Proposal p) {
        if (Boolean.TRUE.equals(p.isCorrect)) {
            System.out.println(GREEN + ">>> ESATTO! Gruppo: " + p.groupTitle + RESET);
            
            // --- FIX TROFEO ---
            // Se il tentativo è corretto E la partita è finita -> Hai vinto!
            if (Boolean.TRUE.equals(p.isFinished)) {
                System.out.println(GREEN + "\n" + p.message + RESET); // Stampa "VITTORIA!" dal server
                printFile(trophyFile);
            }
            // ------------------
            
        } else {
            System.out.println(RED + ">>> SBAGLIATO!" + RESET);
            if (Boolean.TRUE.equals(p.isFinished)) {
                System.out.println(RED + "\n" + p.message + RESET); // Stampa "HAI PERSO"
            }
        }
        
        // Se la proposta porta alla fine della partita e contiene la soluzione (JSON aggiornato), stampala subito
        if (Boolean.TRUE.equals(p.isFinished) && p.solution != null) {
             System.out.println("\n" + PURPLE + "=== SOLUZIONE ===" + RESET);
             for (var g : p.solution) System.out.println(" * " + g.theme + ": " + g.words);
        }
        
        printPrompt();
    }

    @Override
    public void showGameStats(ServerResponse.GameStats s) {
        System.out.println("\n" + CYAN + "=== STATISTICHE PARTITA #" + s.gameId + " ===" + RESET);
        if (s.playersActive != null) System.out.println("Giocatori Attivi: " + s.playersActive);
        if (s.playersFinished != null) System.out.println("Partite Concluse: " + s.playersFinished);
        if (s.playersWon != null) System.out.println("Vittorie: " + GREEN + s.playersWon + RESET);
        if (s.averageScore != null) System.out.printf("Punteggio Medio: %.2f\n", s.averageScore);
        printPrompt();
    }

    @Override
    public void showPlayerStats(ServerResponse.PlayerStats s) {
        System.out.println("\n" + PURPLE + "=== LE TUE STATISTICHE ===" + RESET);
        System.out.printf("Partite: %d | Win Rate: %.1f%%\n", s.puzzlesCompleted, s.winRate); // winRate è già float 0-100 lato server ora? o 0-1? Controlla server.
        // Nel server era: resp.winRate = (won/played) * 100.0f; -> Quindi qui stampiamo diretto
        System.out.printf("Streak: %d (Max: %d) | Perfect: %d\n", s.currentStreak, s.maxStreak, s.perfectPuzzles);
        
        System.out.println("\n" + YELLOW + "Istogramma Errori:" + RESET);
        if (s.mistakeHistogram != null) {
            for (int i = 0; i < s.mistakeHistogram.length; i++) {
                String label = (i == 4) ? "Perso" : (i + " Err ");
                String bar = "█".repeat(Math.max(0, s.mistakeHistogram[i]));
                System.out.printf(" %s | %s (%d)\n", label, GREEN + bar + RESET, s.mistakeHistogram[i]);
            }
        }
        printPrompt();
    }

    @Override
    public void showLeaderboard(ServerResponse.Leaderboard l) {
        System.out.println("\n" + YELLOW + "=== CLASSIFICA ===" + RESET);
        if (l.ranking != null) {
            for (var r : l.ranking) {
                String color = (r.position <= 3) ? GREEN : RESET; 
                System.out.printf("%s#%d %-15s (%d pt)%s\n", color, r.position, r.username, r.score, RESET);
            }
        }
        printPrompt();
    }

    @Override
    public void showNotification(String message) {
        System.out.println("\n" + CYAN + "[NOTIFICA] " + message + RESET);
        printPrompt();
    }

    private void printPrompt() { System.out.print("> "); System.out.flush(); }
    
    private void printGrid(List<String> words, List<ServerResponse.GroupData> correctGroups) {
        Set<String> guessedWords = new HashSet<>();
        if (correctGroups != null) {
            for (var g : correctGroups) {
                if (g.words != null) guessedWords.addAll(g.words);
            }
        }

        int maxLen = 0;
        for (String w : words) if (w != null) maxLen = Math.max(maxLen, w.length());
        
        System.out.println();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String displayVal = (w == null) ? "???" : w;
            String color = guessedWords.contains(w) ? GREEN : RESET; // Verde se indovinata
            
            System.out.print(color + String.format("%-" + (maxLen + 2) + "s", displayVal) + RESET);
            
            if ((i + 1) % 4 == 0) System.out.println();
        }
        System.out.println(); 
    }
    
    private void printFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) System.out.println(YELLOW + line + RESET);
        } catch (IOException e) { 
             System.out.println(YELLOW + "[FILE STAMPATO]" + RESET);
        }
    }
}