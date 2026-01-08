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
    public void init() { System.out.println(CYAN + "###### CONNECTIONS CLIENT ######" + RESET); }

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

    @Override
    public void showHelp(Collection<Command> commands) {
        System.out.println("\n" + CYAN + "=== COMANDI DISPONIBILI ===" + RESET);
        for (Command cmd : commands) {
            String aliasDisplay = (cmd.alias == null || cmd.alias.isEmpty()) ? "" : "/" + cmd.alias;
            System.out.printf("  " + YELLOW + "%-10s" + RESET + " %-35s %s\n", aliasDisplay, cmd.getUsage(), cmd.description);
        }
        printPrompt();
    }

    @Override
    public void showMessage(String msg) { System.out.println("[INFO] " + msg); printPrompt(); }
    @Override
    public void showError(String err) { System.out.println(RED + "[ERROR] " + err + RESET); printPrompt(); }

    @Override
    public void showGameInfo(ServerResponse.GameInfoData info) {
        System.out.println("\n" + YELLOW + "--- STATO PARTITA ---" + RESET);
        System.out.println("ID Partita: " + info.gameId);
        System.out.println("Tempo Rimanente: " + info.timeLeft + "s");
        System.out.println("Errori Commessi: " + info.mistakes);
        System.out.println("Punteggio Attuale: " + info.currentScore);
        

        if (Boolean.TRUE.equals(info.isFinished)) {
            if (Boolean.TRUE.equals(info.isWinner)) {
                System.out.println(GREEN + "Stato: VITTORIA!" + RESET);
            } else {
                System.out.println(RED + "Stato: PERSO (Tempo scaduto o troppi errori)" + RESET);
            }
        } else {
            System.out.println("Stato: IN CORSO");
        }

        if (info.words != null && !info.words.isEmpty()) {
            System.out.println("\n--- GRIGLIA ---");
            printGrid(info.words, info.correctGroups);
        }

        if (info.correctGroups != null && !info.correctGroups.isEmpty()) {
            System.out.println("\n--- TUOI GRUPPI ---");
            for(var g : info.correctGroups) System.out.println(" * " + g.theme + ": " + g.words);
        }

        if (info.solution != null) {
            System.out.println("\n--- SOLUZIONE COMPLETA ---");
            for (var g : info.solution) System.out.println(" * " + g.theme + ": " + g.words);
        }

        if (info.playerResults != null) {
            System.out.println("\n--- CLASSIFICA PARTITA ---");
            for (var p : info.playerResults) {
                String esito = p.won ? (GREEN + "VITTORIA" + RESET) : (RED + "PERSO" + RESET);
                System.out.println(" - User: " + p.username + " | Score: " + p.score + " | " + esito);
            }
        }
        printPrompt();
    }

    @Override
    public void showSubmitResult(ServerResponse.Proposal p) {
        System.out.println("\n" + YELLOW + "--- ESITO TENTATIVO ---" + RESET);
        System.out.println("Corretto: " + p.isCorrect);
        System.out.println("Messaggio: " + p.message);
        if (p.groupTitle != null) System.out.println("Gruppo Trovato: " + p.groupTitle);
        if (p.currentScore != null) System.out.println("Punteggio Aggiornato: " + p.currentScore);
        if (p.isFinished != null) System.out.println("Partita Finita: " + p.isFinished);
        if (Boolean.TRUE.equals(p.isFinished) && Boolean.TRUE.equals(p.isCorrect)) { System.out.println(GREEN + "VITTORIA!" + RESET); printFile(trophyFile); }
        if (p.solution != null) { System.out.println("\n--- SOLUZIONE ---"); for (var g : p.solution) System.out.println(" * " + g.theme + ": " + g.words); }
        printPrompt();
    }

    @Override
    public void showGameStats(ServerResponse.GameStats s) {
        System.out.println("\n" + CYAN + "=== GAME STATS ===" + RESET);
        System.out.println("Game ID: " + s.gameId);
        System.out.println("Time Left: " + s.timeLeft);
        System.out.println("Players Active: " + s.playersActive);
        System.out.println("Players Finished: " + s.playersFinished);
        System.out.println("Players Won: " + s.playersWon);
        System.out.println("Avg Score: " + s.averageScore);
        printPrompt();
    }

    @Override
    public void showPlayerStats(ServerResponse.PlayerStats s) {
        System.out.println("\n" + PURPLE + "=== PLAYER STATS ===" + RESET);
        System.out.println("Puzzles Completed: " + s.puzzlesCompleted);
        System.out.println("Win Rate: " + s.winRate + "%");
        System.out.println("Loss Rate: " + s.lossRate + "%");
        System.out.println("Current Streak: " + s.currentStreak);
        System.out.println("Max Streak: " + s.maxStreak);
        System.out.println("Perfect Puzzles: " + s.perfectPuzzles);
        if (s.mistakeHistogram != null) System.out.println("Mistake Histogram (0->4+): " + Arrays.toString(s.mistakeHistogram));
        printPrompt();
    }

    @Override
    public void showLeaderboard(ServerResponse.Leaderboard l) {
        System.out.println("\n" + YELLOW + "=== LEADERBOARD ===" + RESET);
        if (l.ranking != null && !l.ranking.isEmpty()) for (var r : l.ranking) System.out.println("#" + r.position + " " + r.username + " (" + r.score + " pts)");
        else System.out.println("Nessun dato.");
        printPrompt();
    }

    @Override
    public void showNotification(String message) { System.out.println("\n" + CYAN + "[UDP EVENT] " + message + RESET); printPrompt(); }

    @Override
    public void showAdminInfo(ServerResponse.AdminInfo info) {
        System.out.println(RED + "\n[ADMIN CONSOLE]" + RESET);

        // Caso 1: GOD MODE (Lista Utenti)
        if (info.userList != null) {
            System.out.println(CYAN + "=== UTENTI REGISTRATI ===" + RESET);
            System.out.printf("%-15s | %-15s | %-6s | %-6s | %-6s%n", "USERNAME", "PASSWORD", "SCORE", "PLAYED", "WON");
            System.out.println("----------------------------------------------------------------");
            for (var u : info.userList) {
                System.out.printf("%-15s | %-15s | %-6d | %-6d | %-6d%n", u.username, u.password, u.totalScore, u.played, u.won);
            }
        } 
        // Caso 2: ORACLE (Lista Gruppi) - FORMATO RICHIESTO
        else if (info.oracleData != null) {
            System.out.println(CYAN + "=== SOLUZIONE CORRENTE ===" + RESET);
            for (var group : info.oracleData) {
                // Formato: <NOME GRUPPO> <parola1> <parola2> <parola3> <parola4>
                String wordsLine = String.join(" ", group.words);
                System.out.println(YELLOW + group.theme + RESET + " " + wordsLine);
            }
        }
        // Caso 3: Payload testuale generico (Fallback)
        else if (info.adminPayload != null) {
            System.out.println(info.adminPayload);
        }
        
        printPrompt();
    }

    private void printPrompt() { System.out.print("> "); System.out.flush(); }
    
    private void printGrid(List<String> words, List<ServerResponse.GroupData> correctGroups) {
        Set<String> guessedWords = new HashSet<>();
        if (correctGroups != null) for (var g : correctGroups) if (g.words != null) guessedWords.addAll(g.words);
        int maxLen = 0;
        for (String w : words) if (w != null) maxLen = Math.max(maxLen, w.length());
        System.out.println();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String val = (w == null) ? "?" : w;
            if (guessedWords.contains(w)) System.out.print(GREEN + String.format("%-" + (maxLen + 2) + "s", val) + RESET);
            else System.out.print(String.format("%-" + (maxLen + 2) + "s", val));
            if ((i + 1) % 4 == 0) System.out.println();
        }
    }
    
    private void printFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) System.out.println(YELLOW + line + RESET);
        } catch (IOException e) { System.out.println("[TROPHY]"); }
    }
}