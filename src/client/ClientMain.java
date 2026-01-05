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
            ClientView.printHelp(); // USA IL NUOVO HELP

            new Thread(new ServerListener()).start();

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("> ");
                    if (!scanner.hasNextLine()) break;
                    String line = scanner.nextLine();
                    if (line.trim().isEmpty()) continue;
                    
                    if (!handleCommand(line)) break; 
                }
            } 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean handleCommand(String line) throws IOException {
        String[] parts = line.split("\\s+");
        String cmd = parts[0];
        ClientRequest req = new ClientRequest();

        if ("exit".equalsIgnoreCase(cmd)) { clientChannel.close(); return false; }

        if ("register".equalsIgnoreCase(cmd) && parts.length == 3) {
            req.operation = "register"; req.name = parts[1]; req.psw = parts[2];
        } 
        else if ("login".equalsIgnoreCase(cmd) && parts.length == 3) {
            req.operation = "login"; req.username = parts[1]; req.psw = parts[2];
        }
        else if ("logout".equalsIgnoreCase(cmd)) {
            req.operation = "logout";
        }
        else if ("update_creds".equalsIgnoreCase(cmd) && parts.length == 5) {
            req.operation = "updateCredentials"; req.oldName = parts[1]; req.newName = parts[2]; req.oldPsw = parts[3]; req.newPsw = parts[4];
        }
        else if ("submit".equalsIgnoreCase(cmd) && parts.length == 5) {
            req.operation = "submitProposal";
            req.words = new ArrayList<>(Arrays.asList(parts[1], parts[2], parts[3], parts[4]));
        }
        else if ("info".equalsIgnoreCase(cmd)) {
            req.operation = "requestGameInfo"; req.gameId = 0; 
        }
        else if ("me".equalsIgnoreCase(cmd)) {
            req.operation = "requestPlayerStats";
        }
        else if ("rank".equalsIgnoreCase(cmd)) {
            req.operation = "requestLeaderboard";
        }
        else {
            System.out.println("Comando non valido (usa 'exit' per uscire o controlla gli argomenti).");
            return true;
        }
        
        send(req);
        return true;
    }

    // --- GESTORE RISPOSTE SERVER ---
    static class ServerListener implements Runnable {
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                while (clientChannel.isOpen()) {
                    int read = clientChannel.read(buffer);
                    if (read > 0) {
                        buffer.flip();
                        String json = new String(buffer.array(), 0, read);
                        processResponse(json);
                        System.out.print("> ");
                        buffer.clear();
                    }
                }
            } catch (Exception e) {}
        }

        private void processResponse(String json) {
            try {
                ServerResponse resp = gson.fromJson(json, ServerResponse.class);

                // 1. Messaggi Sistema / Errori
                if (resp.status != null) ClientView.printMessage(resp.status, resp.message);

                // 2. Nuova Partita
                if (resp.message != null && resp.message.contains("NUOVA PARTITA")) {
                    if (resp.gameInfo != null) ClientView.printNewGame(resp.gameInfo.words);
                    else sendRequestInfo(); 
                }

                // 3. Info Partita (Login o Info)
                if (resp.gameInfo != null && (resp.message == null || !resp.message.contains("NUOVA PARTITA"))) {
                    // Se è un update post-submit, viene gestito nel punto 4, 
                    // a meno che non sia una risposta esplicita a "info".
                    // Evitiamo di stampare doppio se è già gestito da isCorrect
                    if (resp.isCorrect == null) {
                        ClientView.printGameInfo(resp.gameInfo.words, resp.gameInfo.mistakes);
                    }
                }

                // 4. Risultato Submit (PUNTO 2)
                if (resp.isCorrect != null) {
                    // Estraiamo le parole rimanenti se il server ce le ha mandate in gameInfo
                    java.util.List<String> remaining = (resp.gameInfo != null) ? resp.gameInfo.words : null;
                    ClientView.printSubmissionResult(resp.isCorrect, resp.groupTitle, resp.currentScore, resp.currentMistakes, remaining);
                }

                // 5. Stato INFO completo
                if (resp.wordsToGroup != null) {
                    ClientView.printGameStatus(resp.timeLeft, resp.currentScore, resp.mistakes, resp.wordsToGroup, Boolean.TRUE.equals(resp.isFinished), resp.correctGroups);
                }
                
                // 6. Soluzione Finale (PUNTO 3)
                if (Boolean.TRUE.equals(resp.isFinished) && resp.solution != null) {
                    // Passiamo score ed errori attuali per il riepilogo
                    ClientView.printSolution(resp.solution, "EVENT".equals(resp.status), resp.currentScore, resp.currentMistakes != 0 ? resp.currentMistakes : resp.mistakes);
                }

                // 7. Statistiche Personali
                if (resp.puzzlesCompleted != null) {
                    ClientView.printStats(resp.puzzlesCompleted, resp.puzzlesWon, resp.winRate, resp.currentStreak, resp.maxStreak, resp.mistakeHistogram);
                }
                
                // 8. Classifica
                if (resp.rankings != null) {
                    System.out.println("--- CLASSIFICA ---");
                    for (ServerResponse.RankingEntry r : resp.rankings) {
                        System.out.println("#" + r.position + " " + r.username + " - Vittorie: " + r.score);
                    }
                    System.out.println("La tua posizione: #" + resp.yourPosition);
                }

            } catch (Exception e) { /* Ignora JSON parziali */ }
        }
        
        private void sendRequestInfo() {
            try {
                ClientRequest req = new ClientRequest();
                req.operation = "requestGameInfo"; req.gameId = 0;
                send(req);
            } catch (IOException e) {}
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
        if (clientChannel.isOpen()) clientChannel.write(ByteBuffer.wrap(gson.toJson(req).getBytes()));
    }
}