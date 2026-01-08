package client.network;

import client.ui.ClientRenderer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.ServerResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Thread che ascolta su connessione TCP tutte le informazioni del server 
 * E le manda alla UI per essere manifestate all'utente
 */
public class TcpListener implements Runnable {
    private final NetworkManager net;
    private final ClientRenderer ui;
    private final int bufferSize;
    private final Gson gson = new Gson();

    public TcpListener(NetworkManager net, ClientRenderer ui, int bufferSize) {
        this.net = net;
        this.ui = ui;
        this.bufferSize = bufferSize;
    }

    @Override
    public void run() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            StringBuilder sb = new StringBuilder();
            
            while (net.getTcpChannel().isOpen()) {
                int read = net.getTcpChannel().read(buffer);

                // gestisco disconnessione del server in modo pulito
                if (read == -1) { 
                    handleDisconnection("Server disconnesso."); 
                    break; 
                }
                
                if (read > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    sb.append(new String(bytes, StandardCharsets.UTF_8));
                    buffer.clear();

                    while (true) {
                        // estraggio il json dalla stringa di risposta
                        int i = sb.indexOf("\n");
                        if (i == -1) break;
                        String json = sb.substring(0, i).trim();
                        sb.delete(0, i + 1);

                        if (!json.isEmpty()) handleResponse(json);
                    }
                }
            }
        } catch (Exception e) {
            if (net.getTcpChannel().isOpen()) handleDisconnection("Errore rete: " + e.getMessage());
        }
    }

    private void handleDisconnection(String reason) {
        ui.showError(reason);
        net.close();
        System.exit(0);
    }

    private void handleResponse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("objectCode")) return; // per essere una risposta valida deve esserci il campo objectCode

            String code = obj.get("objectCode").getAsString(); // trovo il tipo di risposta

            // gestisco ogni risposta 
            switch (code) {
                case "RES_ERROR": ui.showError(gson.fromJson(obj, ServerResponse.Error.class).message); break;
                case "RES_GENERIC": ui.showMessage(gson.fromJson(obj, ServerResponse.Generic.class).message); break;
                case "RES_EVENT": ui.showNotification(gson.fromJson(obj, ServerResponse.Event.class).message); break;
                case "RES_AUTH":
                    ServerResponse.Auth auth = gson.fromJson(obj, ServerResponse.Auth.class);
                    ui.showMessage(auth.message);
                    if (auth.gameInfo != null) ui.showGameInfo(auth.gameInfo);
                    break;
                case "RES_GAME_INFO": ui.showGameInfo(gson.fromJson(obj, ServerResponse.GameInfoData.class)); break;
                case "RES_PROPOSAL": ui.showSubmitResult(gson.fromJson(obj, ServerResponse.Proposal.class)); break;
                case "RES_GAME_STATS": ui.showGameStats(gson.fromJson(obj, ServerResponse.GameStats.class)); break;
                case "RES_PLAYER_STATS": ui.showPlayerStats(gson.fromJson(obj, ServerResponse.PlayerStats.class)); break;
                case "RES_LEADERBOARD": ui.showLeaderboard(gson.fromJson(obj, ServerResponse.Leaderboard.class)); break;
                case "RES_ADMIN": ui.showAdminInfo(gson.fromJson(obj, ServerResponse.AdminInfo.class)); break;
                
                default: System.err.println("Codice sconosciuto: " + code);
            }
        } catch (Exception e) { System.err.println("JSON Error: " + e.getMessage()); }
    }
}