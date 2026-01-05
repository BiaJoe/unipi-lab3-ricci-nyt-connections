package client;

import utils.ServerResponse;
import java.util.List;

public class ClientView {
    
    public static void printHelp() {
        System.out.println("\nComandi disponibili:");
        System.out.println("  register <username> <password>");
        System.out.println("  login <username> <password>");
        System.out.println("  logout");
        System.out.println("  update_creds <oldU> <newU> <oldP> <newP>");
        System.out.println("  submit <w1> <w2> <w3> <w4>");
        System.out.println("  info");
        System.out.println("  me");
        System.out.println("  rank");
        System.out.println("  exit");
    }

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

    public static void printSubmissionResult(boolean isCorrect, String groupTitle, int score, int mistakes, List<String> remainingWords) {
        if (isCorrect) {
            System.out.println("✅ ESATTO! Gruppo trovato: " + groupTitle);
            if (remainingWords != null && !remainingWords.isEmpty()) {
                System.out.println("Griglia aggiornata:");
                printGrid(remainingWords);
            }
        } else {
            System.out.println("❌ SBAGLIATO.");
        }
        System.out.println("Punteggio: " + score + " | Errori: " + mistakes);
    }

    public static void printSolution(List<ServerResponse.GroupData> solution, boolean timeOut, int finalScore, int finalMistakes) {
        System.out.println("\n==========================================");
        System.out.println("⚠️ PARTITA TERMINATA" + (timeOut ? " (Tempo Scaduto)" : "") + " ⚠️");
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
        System.out.println("Partite Giocate: " + played);
        System.out.println("Vittorie: " + won);
        System.out.println("Win Rate: " + String.format("%.1f", winRate) + "%");
        System.out.println("Distribuzione Errori: " + java.util.Arrays.toString(histogram));
    }

    // --- LOGICA DI STAMPA GRIGLIA CON BUCHI ---
    public static void printGrid(List<String> words) {
        if (words == null || words.isEmpty()) return;

        // 1. Calcola lunghezza massima basandosi SOLO sulle parole visibili
        int maxLen = 0;
        for (String w : words) {
            if (w != null && !w.isEmpty() && w.length() > maxLen) {
                maxLen = w.length();
            }
        }
        
        // Fallback se tutte vuote (non dovrebbe succedere se non a fine partita)
        if (maxLen == 0) maxLen = 10; 

        System.out.println();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            
            // Se la parola è vuota (indovinata), stampa uno spazio vuoto grande quanto maxLen
            // Altrimenti stampa la parola con padding
            if (w == null || w.isEmpty()) {
                System.out.printf("%-" + maxLen + "s ", " "); 
            } else {
                System.out.printf("%-" + maxLen + "s ", w);
            }

            // A capo ogni 4
            if ((i + 1) % 4 == 0) {
                System.out.println();
            }
        }
        System.out.println();
    }
}