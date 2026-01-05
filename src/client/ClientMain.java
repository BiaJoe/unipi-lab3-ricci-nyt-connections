package client;

import com.google.gson.Gson;
import utils.ClientRequest;
import utils.ServerResponse;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
    private static DatagramSocket udpSocket;
    private static int localUdpPort;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try {
            readConfig();
            udpSocket = new DatagramSocket();
            localUdpPort = udpSocket.getLocalPort();
            
            clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress(serverAddress, serverPort));

            System.out.println("--- CLIENT CONNESSO ---");
            ClientView.printHelp();

            new Thread(new TcpListener()).start();
            new Thread(new UdpListener()).start();

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
        } finally {
             try { if (udpSocket != null) udpSocket.close(); } catch(Exception e){}
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
            req.udpPort = localUdpPort;
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
            System.out.println("Comando non valido.");
            return true;
        }
        send(req);
        return true;
    }
    
    static class UdpListener implements Runnable {
        public void run() {
            try {
                byte[] buf = new byte[4096];
                while (!udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    ServerResponse resp = gson.fromJson(json, ServerResponse.class);
                    if ("EVENT".equals(resp.status)) {
                        System.out.println("\n\n[NOTIFICA] " + resp.message);
                        if (resp.message.contains("TEMPO SCADUTO") || resp.message.contains("NUOVA PARTITA")) {
                             requestFullInfo();
                        }
                        System.out.print("> ");
                    }
                }
            } catch (Exception e) {}
        }
        private void requestFullInfo() {
            try {
                ClientRequest req = new ClientRequest();
                req.operation = "requestGameInfo"; req.gameId = 0;
                send(req);
            } catch (IOException e) {}
        }
    }

    // --- LISTENER TCP AGGIORNATO (BUFFERING) ---
    static class TcpListener implements Runnable {
        private StringBuilder buffer = new StringBuilder();

        public void run() {
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
                while (clientChannel.isOpen()) {
                    int read = clientChannel.read(byteBuffer);
                    if (read > 0) {
                        byteBuffer.flip();
                        byte[] bytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(bytes);
                        
                        // 1. Accumula
                        buffer.append(new String(bytes));
                        
                        // 2. Processa righe
                        while (true) {
                            int newlineIndex = buffer.indexOf("\n");
                            if (newlineIndex == -1) break;
                            
                            String json = buffer.substring(0, newlineIndex).trim();
                            buffer.delete(0, newlineIndex + 1);
                            
                            if (!json.isEmpty()) processResponse(json);
                        }
                        
                        System.out.print("> ");
                        byteBuffer.clear();
                    }
                }
            } catch (Exception e) {}
        }

        private void processResponse(String json) {
            try {
                ServerResponse resp = gson.fromJson(json, ServerResponse.class);
                if (resp.status != null && !"EVENT".equals(resp.status)) 
                    ClientView.printMessage(resp.status, resp.message);

                if (resp.gameInfo != null && resp.message != null && !resp.message.contains("TEMPO SCADUTO")) {
                     if (resp.isCorrect == null) ClientView.printGameInfo(resp.gameInfo.words, resp.gameInfo.mistakes);
                }
                if (resp.isCorrect != null) {
                    java.util.List<String> remaining = (resp.gameInfo != null) ? resp.gameInfo.words : null;
                    ClientView.printSubmissionResult(resp.isCorrect, resp.groupTitle, resp.currentScore, resp.currentMistakes, remaining);
                }
                if (resp.wordsToGroup != null) {
                    ClientView.printGameStatus(resp.timeLeft, resp.currentScore, resp.mistakes, resp.wordsToGroup, Boolean.TRUE.equals(resp.isFinished), resp.correctGroups);
                }
                if (Boolean.TRUE.equals(resp.isFinished) && resp.solution != null) {
                    ClientView.printSolution(resp.solution, "EVENT".equals(resp.status) || "TEMPO SCADUTO".equals(resp.message), resp.currentScore, resp.currentMistakes);
                }
                if (resp.puzzlesCompleted != null) ClientView.printStats(resp.puzzlesCompleted, resp.puzzlesWon, resp.winRate, resp.currentStreak, resp.maxStreak, resp.mistakeHistogram);
                if (resp.rankings != null) {
                    System.out.println("--- CLASSIFICA ---");
                    for (ServerResponse.RankingEntry r : resp.rankings) {
                        System.out.println("#" + r.position + " " + r.username + " - Vittorie: " + r.score);
                    }
                    System.out.println("La tua posizione: #" + resp.yourPosition);
                }
            } catch (Exception e) {}
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
        // AGGIUNTO \n ALLA FINE DEL MESSAGGIO
        if (clientChannel.isOpen()) clientChannel.write(ByteBuffer.wrap((gson.toJson(req) + "\n").getBytes()));
    }
}