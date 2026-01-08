package client.ui;

import client.logic.CommandProcessor;
import client.ClientConfig;
import client.logic.Command;
import utils.ServerResponse;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Sistema di rendering del client
 */
public class ConsoleUI implements ClientRenderer {

    // ANSI COLORS 
    private static final String RESET  = "\033[0m";
    private static final String CYAN   = "\033[0;36m";
    private static final String RED    = "\033[0;31m";
    private static final String GREEN  = "\033[0;32m";
    private static final String YELLOW = "\033[0;33m";
    private static final String PURPLE = "\033[0;35m";

    // STRINGHE COSTANTI 
    private static final String APP_TITLE = CYAN + "###### The " + YELLOW + "NEW" + CYAN + " New York Times Connections ######" + RESET;
    private static final String HEADER_HELP = "\n" + CYAN + "# COMANDI DISPONIBILI" + RESET;
    
    // Banner
    private static final String BANNER_GAME_ENDED   = RED   + "\n### PARTITA FINITA ##################################################" + RESET;
    private static final String BANNER_GAME_INFO    = CYAN  + "\n### INFO PARTITA ####################################################" + RESET;
    
    // Headers
    private static final String HEADER_GRID         = "\n# GRIGLIA";
    private static final String HEADER_GROUPS       = "\n# GRUPPI INDOVINATI";
    private static final String HEADER_SOLUTION     = "\n# SOLUZIONE";
    private static final String HEADER_MATCH_RANK   = "\n# CLASSIFICA PARTITA";
    private static final String HEADER_GAME_STATS   = "\n" + CYAN + "# GAME STATS" + RESET;
    private static final String HEADER_PLAYER_STATS = "\n" + PURPLE + "# PLAYER STATS" + RESET;
    private static final String HEADER_LEADERBOARD  = "\n" + YELLOW + "# LEADERBOARD" + RESET;
    
    private static final String HEADER_ADMIN_SECRET = RED + "\n# INFORMAZIONI SEGRETE - ACCESSO NON AUTORIZZATO A PLAYER COMUNI" + RESET;
    private static final String HEADER_ADMIN_USERS  = CYAN + "# UTENTI REGISTRATI" + RESET;
    private static final String HEADER_ADMIN_ORACLE = CYAN + "# SOLUZIONE CORRENTE" + RESET;

    // file che contengono immagini decorative txt
    private final String trophyFile;
    private final String skeletonFile; 

    private Integer currentGameId = null;

    public ConsoleUI(ClientConfig config) {
        this.trophyFile = config.trophyFile;
        this.skeletonFile = config.skeletonFile;
    }

    // loop che ascolta da linea di comando e manda i comandi al CommandProcessor
    public void runInputLoop(CommandProcessor processor) {
        displayLn("\n");
        displayLn(APP_TITLE);
        processor.processInput("/help"); // stampo il tutorial
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) { printPrompt(); continue; } // ignoro linee vuote
                if (!processor.processInput(line)) break;
            }
        }
    }

    // stampo le proprietà dei comandi, 
    // se si aggiunge un comando questo si aggiorna da solo
    @Override
    public void showHelp(Collection<Command> commands) {
        displayLn(HEADER_HELP);
        for (Command cmd : commands) {
            String aliasDisplay = (cmd.alias == null || cmd.alias.isEmpty()) ? "" : "/" + cmd.alias;
            displayLn(String.format("  " + YELLOW + "%-10s" + RESET + " %-35s %s", aliasDisplay, cmd.getUsage(), cmd.description));
        }
        displayLn("");
        printPrompt();
    }

    @Override
    public void showMessage(String msg) {
        displayLn("[INFO] " + msg);
        printPrompt();
    }

    @Override
    public void showError(String err) {
        displayLn(RED + "[ERROR] " + err + RESET);
        printPrompt();
    }

    // mostra le informazioni di una partita qualsiasi
    // con un minimo di logica per distinguere partite finite da non finite
    @Override
    public void showGameInfo(ServerResponse.GameInfoData info) {          
        if (currentGameId == null || !currentGameId.equals(info.gameId)) {
            currentGameId = info.gameId; 
            displayLn(BANNER_GAME_INFO); 
        }

        // dati generali
        displayLn(String.format("ID PARTITA        %d", info.gameId));
        displayLn(String.format("Tempo Rimanente   %s", info.isFinished ? "finita" : info.timeLeft + "s"));
        displayLn(String.format("Errori Commessi   %d", info.mistakes));
        displayLn(String.format("Punteggio         %d", info.currentScore));
        
        // parole da ordinare formattate a griglia
        if (info.words != null && !info.words.isEmpty()) {
            displayLn(HEADER_GRID);
            printGrid(info.words, info.correctGroups);
        }
        
        // gruppi già trovati
        if (info.correctGroups != null && !info.correctGroups.isEmpty()) {
            displayLn(HEADER_GROUPS);
            for(var g : info.correctGroups) displayLn(" * " + g.theme + ": " + g.words);
        }

        // se siamo a Partita finita stampo esito, soluìone, risultati di chi ci ha giocato
        if (info.isFinished) {
            String esito = Boolean.TRUE.equals(info.isWinner) ? (GREEN + "VITTORIA!" + RESET) : (RED + "PERSO" + RESET);
            displayLn(String.format("ESITO: %s (Score: %d)", esito, info.currentScore));

            if (info.solution != null) {
                displayLn(HEADER_SOLUTION);
                for (var g : info.solution) displayLn(" * " + g.theme + ": " + g.words);
            }

            if (info.playerResults != null) {
                displayLn(HEADER_MATCH_RANK);
                for (var p : info.playerResults) {
                    String rowOutcome = p.won ? (GREEN + "VITTORIA" + RESET) : (RED + "PERSO" + RESET);
                    displayLn(String.format(" - %-15s Score: %-4d %s", p.username, p.score, rowOutcome));
                }
            }
        }
        printPrompt();
    }

    // dopo un gruppo proposto dice se è giusto o sbagliato
    // la logica serve per stampare la notifica di vittoria o sconfitta
    @Override
    public void showSubmitResult(ServerResponse.Proposal p) {
        String esito = Boolean.TRUE.equals(p.isCorrect) 
            ? GREEN + "ESATTO! Gruppo: " + p.groupTitle + RESET
            : RED   + "SBAGLIATO! " + RESET;
        
        displayLn("\n" + YELLOW + "ESITO TENTATIVO: " + RESET + esito);
        
        // Se la partita è finita
        if (Boolean.TRUE.equals(p.isFinished)) {
            if (Boolean.TRUE.equals(p.isCorrect)) {
                // VITTORIA
                displayLn(GREEN + "\nVITTORIA!!!" + RESET);
                printFile(trophyFile);
            } else {
                // SCONFITTA 
                displayLn(RED + "\nHAI PERSO (Troppi errori)" + RESET);
                printFile(skeletonFile);
            }
            displayLn(BANNER_GAME_ENDED);
        }
        displayLn("");
        printPrompt();
    }

    // Metodi per stampare tabelle di informazioni

    @Override
    public void showGameStats(ServerResponse.GameStats s) {
        displayLn(HEADER_GAME_STATS);
        displayLn(String.format("%-20s %d", "Game ID", s.gameId));
        displayLn(String.format("%-20s %d", "Time Left", s.timeLeft));
        displayLn(String.format("%-20s %d", "Players Active", s.playersActive));
        displayLn(String.format("%-20s %d", "Players Finished", s.playersFinished));
        displayLn(String.format("%-20s %d", "Players Won", s.playersWon));
        displayLn(String.format("%-20s %.2f", "Avg Score", s.averageScore != null ? s.averageScore : 0.0));
        displayLn("");
        printPrompt();
    }

    @Override
    public void showPlayerStats(ServerResponse.PlayerStats s) {
        displayLn(HEADER_PLAYER_STATS);
        displayLn(String.format("%-20s %d", "Puzzles Completed", s.puzzlesCompleted));
        displayLn(String.format("%-20s %.1f%%", "Win Rate", s.winRate));
        displayLn(String.format("%-20s %.1f%%", "Loss Rate", s.lossRate));
        displayLn(String.format("%-20s %d", "Current Streak", s.currentStreak));
        displayLn(String.format("%-20s %d", "Max Streak", s.maxStreak));
        displayLn(String.format("%-20s %d", "Perfect Puzzles", s.perfectPuzzles));
        
        // ISTOGRAMMA con distribuzioni di errori delle vittorie (vinto con 0...4 errori)
        if (s.mistakeHistogram != null) {
            displayLn("\nWinning Mistake Distribution:");
            for (int i = 0; i < s.mistakeHistogram.length; i++) {
                int count = s.mistakeHistogram[i];
                String label = String.valueOf(i); //  0, 1, 2, 3, 4
                
                int visualCount = Math.min(count, 30);
                String bar = "#".repeat(Math.max(0, visualCount));
                
                displayLn(String.format("%-2s %-30s (%d)", label, bar, count));
            }
        }
        displayLn("");
        printPrompt();
    }

    @Override
    public void showLeaderboard(ServerResponse.Leaderboard l) {
        displayLn(HEADER_LEADERBOARD);
        if (l.ranking != null && !l.ranking.isEmpty()) {
            displayLn(String.format("%-4s %-15s %s", "POS", "USER", "SCORE"));
            displayLn("----------------------------");
            for (var r : l.ranking) {
                displayLn(String.format("#%-3d %-15s %d", r.position, r.username, r.score));
            }
        } else {
            displayLn("Nessun dato.");
        }
        displayLn("");
        printPrompt();
    }

    // notifiche UDP ricevute dal server
    // passano tutte per qui, 
    // è sufficiente modificare queste due righe per non farle apparire
    @Override
    public void showNotification(String message) {
        displayLn("\n" + CYAN + "[NOTIFICA] " + message + RESET); 
        printPrompt();
    }

    // metodo per mostrare informazioni richieste dan un Admin user
    @Override
    public void showAdminInfo(ServerResponse.AdminInfo info) {
        displayLn(HEADER_ADMIN_SECRET);
        if (info.userList != null) {
            displayLn(HEADER_ADMIN_USERS);
            displayLn(String.format(YELLOW + "%-15s %-15s %-6s %-6s %-6s%n" + RESET, "USERNAME", "PASSWORD", "SCORE", "PLAYED", "WON"));
            for (var u : info.userList) {
                displayLn(String.format("%-15s %-15s %-6d %-6d %-6d", u.username, u.password, u.totalScore, u.played, u.won));
            }
        } else if (info.oracleData != null) {
            displayLn(HEADER_ADMIN_ORACLE);
            for (var group : info.oracleData) {
                String wordsLine = String.join(" ", group.words);
                displayLn(YELLOW + group.theme + RESET + " " + wordsLine);
            }
        } else if (info.adminPayload != null) {
            displayLn(info.adminPayload);
        }
        displayLn("");
        printPrompt();
    }

    private void printPrompt() { 
        System.out.printf("$ ");   
    }
    
    // metodo per stampare la griglia di parole versione pretty
    private void printGrid(List<String> words, List<ServerResponse.GroupData> correctGroups) {
        Set<String> guessedWords = new HashSet<>();
        if (correctGroups != null) {
            for (var g : correctGroups) if (g.words != null) guessedWords.addAll(g.words);
        }

        int maxLen = 0;
        for (String w : words) if (w != null) maxLen = Math.max(maxLen, w.length());
        
        displayLn("");
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String val = (w == null) ? "?" : w;
            if (guessedWords.contains(w)) display(GREEN + String.format("%-" + (maxLen + 2) + "s", val) + RESET);
            else display(String.format("%-" + (maxLen + 2) + "s", val));
            if ((i + 1) % 4 == 0) displayLn("");
        }
        displayLn("");
    }
    
    private void printFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) displayLn(YELLOW + line + RESET);
        } catch (IOException e) { 
             displayLn("[FILE NOT FOUND: " + path + "]"); 
        }
    }

    // wrapper di Stdout, 
    // in implementazioni future potrebbero essere l'inizio 
    // di un sistema UI più complesso

    private void displayLn(String msg) {
        display(msg + "\n");
    }

    private void display(String msg) {
        try {
            System.out.print(msg);
            System.out.flush();
        } catch (Exception e) { }
    }
}