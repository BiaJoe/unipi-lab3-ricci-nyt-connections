package client;

import utils.ServerResponse;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class ClientView {

    // Ora accetta il path dal Main
    public static void printHelp(String helpFilePath) {
        printFile(helpFilePath);
    }

    // Ora accetta il path dal Main
    public static void printSubmissionResult(boolean isCorrect, String groupTitle, int score, int mistakes, List<String> remainingWords, String trophyFilePath) {
        if (isCorrect) {
            System.out.println("ESATTO! Gruppo trovato: " + groupTitle);
            
            if (score == 4) {
                printFile(trophyFilePath); // Stampa dal file configurato
            } else if (remainingWords != null && !remainingWords.isEmpty()) {
                System.out.println("Griglia aggiornata:");
                printGrid(remainingWords);
            }
        } else {
            System.out.println("SBAGLIATO.");
        }
        System.out.println("Punteggio: " + score + " | Errori: " + mistakes);
    }

    private static void printFile(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("(!) Impossibile caricare risorsa grafica: " + filename);
        }
    }

    // --- (GLI ALTRI METODI RIMANGONO IDENTICI A PRIMA) ---
    public static void printMessage(String status, String message) {
        System.out.println("\n[SERVER] " + status + (message != null ? ": " + message : ""));
    }

    public static void printNewGame(List<String> words) {
        System.out.println("\n--- NUOVA PARTITA INIZIATA ---");
        printGrid(words);
        System.out.println("Errori commessi: 0");
    }

    public static void printGameInfo(List<String> words, int mistakes) {
        System.out.println("\n--- DETTAGLI PARTITA ---");
        printGrid(words);
        System.out.println("Errori commessi: " + mistakes);
    }

    public static void printGameStatus(int timeLeft, int score, int mistakes, List<String> words, boolean isFinished, List<ServerResponse.GroupData> foundGroups) {
        System.out.println("\n--- STATO PARTITA ---");
        System.out.println("Tempo rimasto: " + timeLeft + "s");

        if (!isFinished && words != null) {
            System.out.println("Parole da trovare:");
            printGrid(words);
        }
        
        System.out.println("Punteggio: " + score + " | Errori commessi: " + mistakes);
        
        if (foundGroups != null && !foundGroups.isEmpty()) {
            System.out.println("Gruppi trovati:");
            for (ServerResponse.GroupData g : foundGroups) {
                System.out.println(" - " + g.title + ": " + g.words);
            }
        }
    }

    public static void printSolution(List<ServerResponse.GroupData> solution, boolean timeOut, int finalScore, int finalMistakes) {
        System.out.println("\n==========================================");
        System.out.println("PARTITA TERMINATA" + (timeOut ? " (Tempo Scaduto)" : ""));
        System.out.println("RISULTATO FINALE -> Punteggio: " + finalScore + " | Errori: " + finalMistakes);
        
        if (solution != null) {
            System.out.println("\nSoluzione Completa:");
            for (ServerResponse.GroupData g : solution) {
                System.out.println(" * " + g.title + ": " + g.words);
            }
        }
        System.out.println("==========================================\n");
    }
    
    public static void printStats(int played, int won, double winRate, int currentStreak, int maxStreak, int[] histogram) {
        System.out.println("\n--- LE TUE STATISTICHE GLOBALI ---");
        System.out.printf("Partite Giocate: %d%n", played);
        System.out.printf("Vittorie: %d%n", won);
        System.out.printf("Win Rate: %.1f%%%n", winRate);
        System.out.println("Distribuzione Errori: " + java.util.Arrays.toString(histogram));
    }

    public static void printLeaderboard(List<ServerResponse.RankingEntry> rankings, int yourPos) {
        System.out.println("\n--- CLASSIFICA ---");
        for (ServerResponse.RankingEntry r : rankings) {
            System.out.printf("#%d %s - Vittorie: %d%n", r.position, r.username, r.score);
        }
        System.out.println("La tua posizione: #" + yourPos);
    }

    public static void printGrid(List<String> words) {
        if (words == null || words.isEmpty()) return;

        int maxLen = 0;
        for (String w : words) {
            if (w != null && !w.isEmpty()) maxLen = Math.max(maxLen, w.length());
        }
        if (maxLen == 0) maxLen = 10; 

        System.out.println();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String val = (w == null || w.isEmpty()) ? " " : w;
            System.out.printf("%-" + maxLen + "s ", val);

            if ((i + 1) % 4 == 0) System.out.println();
        }
        System.out.println();
    }
}