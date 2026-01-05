package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import server.models.ClientSession; // Import necessario
import server.models.Game;
import utils.ServerResponse;

import java.io.FileReader;

public class GameScheduler implements Runnable {
    private final ServerMain server;
    private volatile boolean running = true;
    private final Gson gson = new Gson();

    public GameScheduler(ServerMain server) {
        this.server = server;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        System.out.println("Avvio Game Scheduler...");

        while (running) {
            try (JsonReader reader = new JsonReader(new FileReader(ServerConfig.DATA_FILE_PATH))) {
                reader.beginArray();
                
                while (reader.hasNext() && running) {
                    Game g = gson.fromJson(reader, Game.class);
                    
                    // 1. Imposta la nuova partita nel Manager (pulisce activePlayers)
                    GameManager.getInstance().setCurrentGame(g);
                    
                    // 2. [FIX] RESETTA LE SESSIONI DEI CLIENT CONNESSI
                    // Se non facciamo questo, i client connessi pensano di aver ancora vinto la partita vecchia
                    for (ClientSession session : server.getAllSessions()) {
                        session.resetGameStatus();
                    }

                    System.out.println("--- NUOVA PARTITA ID: " + g.getGameId() + " ---");
                    
                    // 3. Notifica UDP
                    notifyNewGameUDP();

                    try {
                        Thread.sleep(ServerConfig.GAME_DURATION * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // 4. Fine Partita
                    handleGameEndUDP();
                }
                
                System.out.println("--- FINE FILE PARTITE: RICOMINCIO DALL'INIZIO ---");
                
            } catch (Exception e) {
                if (running) {
                    e.printStackTrace();
                    try { Thread.sleep(5000); } catch (InterruptedException ie) {}
                }
            }
        }
    }

    private void notifyNewGameUDP() {
        ServerResponse resp = new ServerResponse();
        resp.status = "EVENT";
        resp.message = "NUOVA PARTITA INIZIATA!";
        server.sendUdpBroadcast(gson.toJson(resp));
    }

    private void handleGameEndUDP() {
        // Aggiorna statistiche per chi non ha finito
        for(ClientSession session : server.getAllSessions()) {
             if(session.isLoggedIn() && !session.isGameFinished()) {
                 UserManager.getInstance().updateStatsTimeOut(session.getUsername());
             }
        }

        ServerResponse resp = new ServerResponse();
        resp.status = "EVENT";
        resp.message = "TEMPO SCADUTO";
        resp.isFinished = true;
        
        server.sendUdpBroadcast(gson.toJson(resp));
    }
}