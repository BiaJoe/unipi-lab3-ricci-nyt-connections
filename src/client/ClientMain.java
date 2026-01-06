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
import java.util.*;

public class ClientMain {
    public static final String configFile = "client.properties";
    
    // --- CONFIGURAZIONE ---
    public static String serverAddress;
    public static int serverPort;
    public static int connectTimeout;
    public static int tcpBufferSize;
    public static int udpBufferSize;
    public static String helpFile;
    public static String trophyFile;

    private static SocketChannel clientChannel;
    private static DatagramSocket udpSocket;
    private static int localUdpPort;
    private static final Gson gson = new Gson();

    // Mappa Comandi
    private static final Map<String, CommandHandler> commandMap = new HashMap<>();

    @FunctionalInterface
    interface CommandHandler {
        boolean execute(String[] args) throws IOException;
    }

    public static void main(String[] args) {
        try {
            readConfig();
            initCommandMap();
            
            udpSocket = new DatagramSocket();
            localUdpPort = udpSocket.getLocalPort();
            
            clientChannel = SocketChannel.open();
            clientChannel.socket().connect(new InetSocketAddress(serverAddress, serverPort), connectTimeout);
            clientChannel.configureBlocking(true);

            System.out.println("--- CLIENT CONNESSO ---");
            ClientView.printHelp(helpFile);

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
             try { if (clientChannel != null) clientChannel.close(); } catch(Exception e){}
        }
    }

    // --- LISTENER UDP ---
    static class UdpListener implements Runnable {
        public void run() {
            try {
                byte[] buf = new byte[udpBufferSize];
                while (!udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    ServerResponse resp = gson.fromJson(json, ServerResponse.class);
                    
                    if ("EVENT".equals(resp.status)) {
                        System.out.println("\n\n[NOTIFICA] " + resp.message);
                        handleUdpEvent(resp);
                        System.out.print("> ");
                    }
                }
            } catch (Exception e) {}
        }

        private void handleUdpEvent(ServerResponse resp) {
            if (resp.message.contains("TEMPO SCADUTO") && resp.solution != null) {
                // Caso Fine Partita: Stampa soluzione locale
                ClientView.printSolution(resp.solution, true, 0, 0);
            } else if (resp.message.contains("NUOVA PARTITA")) {
                // Caso Nuova Partita: Richiedi info aggiornate al server
                // FIX: ORA USIAMO IL METODO CHE PRIMA ERA INUTILIZZATO
                requestFullInfo(); 
            }
        }
        
        // Questo metodo ora viene usato correttamente qui sopra
        private void requestFullInfo() {
            try { 
                send(simpleReq("requestGameInfo")); 
            } catch (IOException e) {
                // Gestione silenziosa errore socket
            }
        }
    }

    // --- LISTENER TCP ---
    static class TcpListener implements Runnable {
        private StringBuilder buffer = new StringBuilder();

        public void run() {
            try {
                ByteBuffer byteBuffer = ByteBuffer.allocate(tcpBufferSize);
                while (clientChannel.isOpen()) {
                    int read = clientChannel.read(byteBuffer);
                    if (read > 0) {
                        byteBuffer.flip();
                        byte[] bytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(bytes);
                        buffer.append(new String(bytes));
                        
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
                handleSystemMsg(resp);
                handleGameUpdate(resp);
                handleSubmission(resp);
                handleFinalSolution(resp);
                handleStatsAndRank(resp);
            } catch (Exception e) { }
        }

        private void handleSystemMsg(ServerResponse resp) {
            if (resp.status != null && !"EVENT".equals(resp.status)) 
                ClientView.printMessage(resp.status, resp.message);
        }

        private void handleGameUpdate(ServerResponse resp) {
            if (resp.gameInfo != null && resp.message != null && !resp.message.contains("TEMPO SCADUTO") && resp.isCorrect == null) {
                 ClientView.printGameInfo(resp.gameInfo.words, resp.gameInfo.mistakes);
            }
            if (resp.wordsToGroup != null) {
                ClientView.printGameStatus(resp.timeLeft, resp.currentScore, resp.mistakes, resp.wordsToGroup, Boolean.TRUE.equals(resp.isFinished), resp.correctGroups);
            }
        }

        private void handleSubmission(ServerResponse resp) {
            if (resp.isCorrect != null) {
                List<String> remaining = (resp.gameInfo != null) ? resp.gameInfo.words : null;
                ClientView.printSubmissionResult(resp.isCorrect, resp.groupTitle, resp.currentScore, resp.currentMistakes, remaining, trophyFile);
            }
        }

        private void handleFinalSolution(ServerResponse resp) {
            if (Boolean.TRUE.equals(resp.isFinished) && resp.solution != null) {
                ClientView.printSolution(resp.solution, "EVENT".equals(resp.status) || (resp.message != null && resp.message.contains("TEMPO")), resp.currentScore, resp.currentMistakes);
            }
        }

        private void handleStatsAndRank(ServerResponse resp) {
            if (resp.puzzlesCompleted != null) {
                ClientView.printStats(resp.puzzlesCompleted, resp.puzzlesWon, resp.winRate, resp.currentStreak, resp.maxStreak, resp.mistakeHistogram);
            }
            if (resp.rankings != null) {
                ClientView.printLeaderboard(resp.rankings, resp.yourPosition);
            }
        }
    }

    // --- CONFIGURAZIONE ---
    private static void readConfig() throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);
            serverAddress = prop.getProperty("serverAddress", "127.0.0.1");
            serverPort = Integer.parseInt(prop.getProperty("serverPort", "8080"));
            connectTimeout = Integer.parseInt(prop.getProperty("connectTimeout", "5000"));
            tcpBufferSize = Integer.parseInt(prop.getProperty("tcpBufferSize", "8192"));
            udpBufferSize = Integer.parseInt(prop.getProperty("udpBufferSize", "4096"));
            helpFile = prop.getProperty("helpFile", "help.txt");
            trophyFile = prop.getProperty("trophyFile", "trophy.txt");
        }
    }

    // --- COMANDI ---
    private static void initCommandMap() {
        commandMap.put("exit", args -> { clientChannel.close(); return false; });
        commandMap.put("register", args -> {
            if (args.length != 3) return invalidArgs();
            send(req("register", args[1], args[2], 0));
            return true;
        });
        commandMap.put("login", args -> {
            if (args.length != 3) return invalidArgs();
            ClientRequest r = req("login", null, null, localUdpPort);
            r.username = args[1]; r.psw = args[2];
            send(r);
            return true;
        });
        commandMap.put("logout", args -> { send(req("logout", null, null, 0)); return true; });
        commandMap.put("update_creds", args -> {
            if (args.length != 5) return invalidArgs();
            ClientRequest r = new ClientRequest();
            r.operation = "updateCredentials";
            r.oldName = args[1]; r.newName = args[2]; r.oldPsw = args[3]; r.newPsw = args[4];
            send(r);
            return true;
        });
        commandMap.put("submit", args -> {
            if (args.length != 5) return invalidArgs();
            ClientRequest r = new ClientRequest();
            r.operation = "submitProposal";
            r.words = new ArrayList<>(Arrays.asList(args[1], args[2], args[3], args[4]));
            send(r);
            return true;
        });
        commandMap.put("info", args -> { send(simpleReq("requestGameInfo")); return true; });
        commandMap.put("me", args -> { send(simpleReq("requestPlayerStats")); return true; });
        commandMap.put("rank", args -> { send(simpleReq("requestLeaderboard")); return true; });
    }

    private static boolean handleCommand(String line) throws IOException {
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();
        CommandHandler handler = commandMap.get(cmd);
        if (handler != null) return handler.execute(parts);
        else { System.out.println("Comando non valido."); return true; }
    }

    private static boolean invalidArgs() { System.out.println("(!) Argomenti non validi. Controlla help."); return true; }
    
    private static ClientRequest req(String op, String name, String psw, int port) {
        ClientRequest r = new ClientRequest(); r.operation = op; r.name = name; r.psw = psw; r.udpPort = port; return r;
    }
    
    private static ClientRequest simpleReq(String op) {
        ClientRequest r = new ClientRequest(); r.operation = op; r.gameId = 0; return r;
    }

    private static void send(ClientRequest req) throws IOException {
        if (clientChannel.isOpen()) clientChannel.write(ByteBuffer.wrap((gson.toJson(req) + "\n").getBytes()));
    }
}