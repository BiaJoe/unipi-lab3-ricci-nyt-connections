package client;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;

public class ClientMain {
    public static final String configFile = "client.properties";
    public static String serverAddress;
    public static int serverPort;

    private static SocketChannel clientChannel;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try {
            readConfig();
            clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress(serverAddress, serverPort));

            System.out.println("--- CLIENT CONNESSO ---");
            System.out.println("Comandi disponibili:");
            System.out.println("1. register <name> <psw>");
            System.out.println("2. login <user> <psw>");
            System.out.println("3. logout");
            System.out.println("4. update_creds <oldN> <newN> <oldP> <newP>");
            System.out.println("5. submit <w1> <w2> <w3> <w4>");
            System.out.println("6. info (Stato Partita)");
            System.out.println("7. me (Statistiche Personali)");
            System.out.println("8. rank (Classifica)");
            System.out.println("9. exit");

            new Thread(new ServerListener()).start();

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("> ");
                    if (!scanner.hasNextLine()) break;
                    
                    String line = scanner.nextLine();
                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    String cmd = parts[0];

                    if ("exit".equalsIgnoreCase(cmd)) { 
                        clientChannel.close(); 
                        break; 
                    }

                    ClientRequest req = new ClientRequest();

                    if ("register".equalsIgnoreCase(cmd) && parts.length == 3) {
                        req.operation = "register"; req.name = parts[1]; req.psw = parts[2];
                        send(req);
                    } 
                    else if ("login".equalsIgnoreCase(cmd) && parts.length == 3) {
                        req.operation = "login"; req.username = parts[1]; req.psw = parts[2];
                        send(req);
                    }
                    else if ("logout".equalsIgnoreCase(cmd)) {
                        req.operation = "logout";
                        send(req);
                    }
                    else if ("update_creds".equalsIgnoreCase(cmd) && parts.length == 5) {
                        req.operation = "updateCredentials"; req.oldName = parts[1]; req.newName = parts[2]; req.oldPsw = parts[3]; req.newPsw = parts[4];
                        send(req);
                    }
                    else if ("submit".equalsIgnoreCase(cmd) && parts.length == 5) {
                        req.operation = "submitProposal";
                        req.words = new ArrayList<>(Arrays.asList(parts[1], parts[2], parts[3], parts[4]));
                        send(req);
                    }
                    else if ("info".equalsIgnoreCase(cmd)) {
                        req.operation = "requestGameInfo"; req.gameId = 0; 
                        send(req);
                    }
                    else if ("me".equalsIgnoreCase(cmd)) {
                        req.operation = "requestPlayerStats";
                        send(req);
                    }
                    else if ("rank".equalsIgnoreCase(cmd)) {
                        req.operation = "requestLeaderboard";
                        send(req);
                    }
                    else if ("game_stats".equalsIgnoreCase(cmd)) {
                        req.operation = "requestGameStats";
                        send(req);
                    }
                    else {
                        System.out.println("Comando non valido o argomenti errati.");
                    }
                }
            } 

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readConfig() throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);
            serverAddress = prop.getProperty("serverAddress");
            serverPort = Integer.parseInt(prop.getProperty("serverPort"));
        }
    }

    private static void send(ClientRequest req) throws IOException {
        if (clientChannel.isOpen()) {
            String json = gson.toJson(req);
            clientChannel.write(ByteBuffer.wrap(json.getBytes()));
        }
    }

    // --- GESTIONE RISPOSTE SERVER ---
    static class ServerListener implements Runnable {
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (clientChannel.isOpen()) {
                    int read = clientChannel.read(buffer);
                    if (read > 0) {
                        buffer.flip();
                        String json = new String(buffer.array(), 0, read);
                        
                        try {
                            ServerResponse resp = gson.fromJson(json, ServerResponse.class);
                            
                            // 1. Messaggi di Sistema / Errori
                            if (resp.status != null) {
                                System.out.println("\n[SERVER] " + resp.status + (resp.message != null ? ": " + resp.message : ""));
                            }

                            // 2. GESTIONE AUTOMATICA "NUOVA PARTITA"
                            // Se il server ci manda già i dati (gameInfo) nel messaggio di notifica, li usiamo subito!
                            if (resp.message != null && resp.message.contains("NUOVA PARTITA")) {
                                if (resp.gameInfo != null) {
                                    System.out.println("--- NUOVA PARTITA INIZIATA ---");
                                    printGameInfo(resp.gameInfo.words, 0); // 0 errori all'inizio
                                } else {
                                    // Fallback: se il server non ha mandato i dati, li richiediamo
                                    ClientRequest req = new ClientRequest();
                                    req.operation = "requestGameInfo";
                                    req.gameId = 0;
                                    send(req); 
                                }
                            }

                            // 3. Risposta al LOGIN / Richiesta INFO esplicita
                            // Nota: controlliamo !contains("NUOVA PARTITA") per non stampare due volte se gestito sopra
                            if (resp.gameInfo != null && (resp.message == null || !resp.message.contains("NUOVA PARTITA"))) {
                                System.out.println("--- DETTAGLI PARTITA ---");
                                printGameInfo(resp.gameInfo.words, resp.gameInfo.mistakes);
                            }

                            // 4. Risposta a SUBMIT (Tentativo)
                            if (resp.isCorrect != null) {
                                if (resp.isCorrect) {
                                    System.out.println("✅ ESATTO! Gruppo trovato: " + resp.groupTitle);
                                } else {
                                    System.out.println("❌ SBAGLIATO.");
                                }
                                System.out.println("Punteggio: " + resp.currentScore + " | Errori: " + resp.currentMistakes);
                            }

                            // 5. Risposta a INFO (Stato completo)
                            if (resp.wordsToGroup != null) {
                                System.out.println("--- STATO PARTITA ---");
                                System.out.println("Tempo rimasto: " + resp.timeLeft + "s");
   
                                if (!Boolean.TRUE.equals(resp.isFinished)) {
                                     System.out.println("Parole da trovare:");
                                     printGrid(resp.wordsToGroup); 
                                }
                                
                                System.out.println("Punteggio: " + resp.currentScore + " | Errori commessi: " + resp.mistakes);
                                
                                if (resp.correctGroups != null && !resp.correctGroups.isEmpty()) {
                                    System.out.println("Gruppi trovati:");
                                    for (ServerResponse.GroupData g : resp.correctGroups) {
                                        System.out.println(" - " + g.title + ": " + g.words);
                                    }
                                }
                                
                                // Gestione Fine Partita dentro INFO
                                if (Boolean.TRUE.equals(resp.isFinished)) {
                                    System.out.println("⚠️ PARTITA TERMINATA ⚠️");
                                    if (resp.solution != null) {
                                        System.out.println("Soluzione:");
                                        for (ServerResponse.GroupData g : resp.solution) {
                                            System.out.println(" * " + g.title + ": " + g.words);
                                        }
                                    }
                                }
                            }

                            // 6. Soluzione Finale inviata come EVENTO (Timeout)
                            if (Boolean.TRUE.equals(resp.isFinished) && resp.solution != null && resp.status.equals("EVENT")) {
                                System.out.println("⚠️ PARTITA TERMINATA (Tempo Scaduto) ⚠️");
                                System.out.println("Soluzione:");
                                for (ServerResponse.GroupData g : resp.solution) {
                                    System.out.println(" * " + g.title + ": " + g.words);
                                }
                            }

                            // 7. Statistiche Personali
                            if (resp.puzzlesCompleted != null) {
                                System.out.println("--- LE TUE STATISTICHE ---");
                                System.out.println("Partite Giocate: " + resp.puzzlesCompleted);
                                System.out.println("Vittorie: " + resp.puzzlesWon);
                                System.out.println("Win Rate: " + String.format("%.1f", resp.winRate) + "%");
                                System.out.println("Streak Corrente: " + resp.currentStreak);
                                System.out.println("Streak Max: " + resp.maxStreak);
                                System.out.println("Distribuzione Errori: " + Arrays.toString(resp.mistakeHistogram));
                            }

                            // 8. Classifica
                            if (resp.rankings != null) {
                                System.out.println("--- CLASSIFICA ---");
                                for (ServerResponse.RankingEntry r : resp.rankings) {
                                    System.out.println("#" + r.position + " " + r.username + " - Vittorie: " + r.score);
                                }
                                System.out.println("La tua posizione: #" + resp.yourPosition);
                            }

                        } catch (Exception e) {
                             // System.out.println("[DEBUG RAW]: " + json);
                        }
                        
                        System.out.print("> ");
                        buffer.clear();
                    }
                }
            } catch (Exception e) {}
        }
    }
    
    // Helper per stampare info base partita
    private static void printGameInfo(java.util.List<String> words, int mistakes) {
        printGrid(words);
        System.out.println("Errori commessi: " + mistakes);
    }

    private static void printGrid(java.util.List<String> words) {
        if (words == null || words.isEmpty()) return;

        int maxLen = 0;
        for (String w : words) {
            if (w.length() > maxLen) maxLen = w.length();
        }

        System.out.println(); 
        for (int i = 0; i < words.size(); i++) {
            System.out.printf("%-" + maxLen + "s ", words.get(i));
            if ((i + 1) % 4 == 0) {
                System.out.println();
            }
        }
        System.out.println();
    }
}