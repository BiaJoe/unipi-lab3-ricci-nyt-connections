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

            // Try-with-resources per chiudere correttamente lo scanner
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
                        req.operation = "register";
                        req.name = parts[1];
                        req.psw = parts[2];
                        send(req);
                    } 
                    else if ("login".equalsIgnoreCase(cmd) && parts.length == 3) {
                        req.operation = "login";
                        req.username = parts[1];
                        req.psw = parts[2];
                        send(req);
                    }
                    else if ("logout".equalsIgnoreCase(cmd)) {
                        req.operation = "logout";
                        send(req);
                    }
                    else if ("update_creds".equalsIgnoreCase(cmd) && parts.length == 5) {
                        req.operation = "updateCredentials";
                        req.oldName = parts[1];
                        req.newName = parts[2];
                        req.oldPsw = parts[3];
                        req.newPsw = parts[4];
                        send(req);
                    }
                    else if ("submit".equalsIgnoreCase(cmd) && parts.length == 5) {
                        req.operation = "submitProposal";
                        req.words = new ArrayList<>(Arrays.asList(parts[1], parts[2], parts[3], parts[4]));
                        send(req);
                    }
                    else if ("info".equalsIgnoreCase(cmd)) {
                        req.operation = "requestGameInfo";
                        req.gameId = 0; 
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
        String json = gson.toJson(req);
        clientChannel.write(ByteBuffer.wrap(json.getBytes()));
    }

    // --- GESTIONE RISPOSTE SERVER ---
    static class ServerListener implements Runnable {
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(8192); // Buffer ampio
                while (clientChannel.isOpen()) {
                    int read = clientChannel.read(buffer);
                    if (read > 0) {
                        buffer.flip();
                        String json = new String(buffer.array(), 0, read);
                        
                        try {
                            ServerResponse resp = gson.fromJson(json, ServerResponse.class);
                            
                            // Stampa Status Generico (o Evento)
                            System.out.println("\n[SERVER] " + resp.status + (resp.message != null ? ": " + resp.message : ""));

                            // --- AUTOMAZIONE: Se inizia nuova partita, chiedi info ---
                            if (resp.message != null && resp.message.contains("NUOVA PARTITA")) {
                                ClientRequest req = new ClientRequest();
                                req.operation = "requestGameInfo";
                                req.gameId = 0;
                                send(req); 
                            }

                            // 1. Risposta al LOGIN (gameInfo)
                            if (resp.gameInfo != null) {
                                System.out.println("--- BENVENUTO IN PARTITA ---");
                                System.out.println("Parole: " + resp.gameInfo.words);
                                System.out.println("Errori: " + resp.gameInfo.mistakes);
                            }

                            // 2. Risposta a SUBMIT (isCorrect)
                            if (resp.isCorrect != null) {
                                if (resp.isCorrect) {
                                    System.out.println("✅ ESATTO! Gruppo trovato: " + resp.groupTitle);
                                } else {
                                    System.out.println("❌ SBAGLIATO.");
                                }
                                System.out.println("Punteggio: " + resp.currentScore + " | Errori: " + resp.currentMistakes);
                            }

                            // 3. Risposta a INFO (requestGameInfo)
                            if (resp.wordsToGroup != null) {
                                System.out.println("--- STATO PARTITA ---");
                                System.out.println("Tempo rimasto: " + resp.timeLeft + "s");
                                System.out.println("Punteggio: " + resp.currentScore + " | Errori commessi: " + resp.mistakes);
                                System.out.println("Parole da trovare: " + resp.wordsToGroup);
                                if (resp.correctGroups != null && !resp.correctGroups.isEmpty()) {
                                    System.out.println("Gruppi trovati:");
                                    for (ServerResponse.GroupData g : resp.correctGroups) {
                                        System.out.println(" - " + g.title + ": " + g.words);
                                    }
                                }
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

                            // 4. Risposta a ME (requestPlayerStats)
                            if (resp.puzzlesCompleted != null) {
                                System.out.println("--- LE TUE STATISTICHE ---");
                                System.out.println("Partite Giocate: " + resp.puzzlesCompleted);
                                System.out.println("Vittorie: " + resp.puzzlesWon);
                                System.out.println("Win Rate: " + String.format("%.1f", resp.winRate) + "%");
                                System.out.println("Streak Corrente: " + resp.currentStreak);
                                System.out.println("Streak Max: " + resp.maxStreak);
                                System.out.println("Distribuzione Errori: " + Arrays.toString(resp.mistakeHistogram));
                            }

                            // 5. Risposta a RANK (requestLeaderboard)
                            if (resp.rankings != null) {
                                System.out.println("--- CLASSIFICA ---");
                                for (ServerResponse.RankingEntry r : resp.rankings) {
                                    System.out.println("#" + r.position + " " + r.username + " - Vittorie: " + r.score);
                                }
                                System.out.println("La tua posizione: #" + resp.yourPosition);
                            }

                        } catch (Exception e) {
                             System.out.println("\n[RAW MSG]: " + json);
                        }
                        
                        System.out.print("> ");
                        buffer.clear();
                    }
                }
            } catch (Exception e) {}
        }
    }
}