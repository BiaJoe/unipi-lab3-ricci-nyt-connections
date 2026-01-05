package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import server.models.Game;
import server.models.Group;
import utils.ServerResponse;

import java.io.FileReader;
import java.util.ArrayList;

public class GameScheduler implements Runnable {
    private final ServerMain server;
    private volatile boolean running = true;

    public GameScheduler(ServerMain server) {
        this.server = server;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        Gson gson = new Gson();
        System.out.println("Avvio Game Scheduler...");

        while (running) {
            // Usa ServerConfig per il percorso
            try (JsonReader reader = new JsonReader(new FileReader(ServerConfig.DATA_FILE_PATH))) {
                reader.beginArray();
                
                while (reader.hasNext() && running) {
                    Game g = gson.fromJson(reader, Game.class);
                    
                    // --- MODIFICA: Usa GameManager per impostare la partita ---
                    GameManager.getInstance().setCurrentGame(g);
                    // Non serve resetGameTimer(), lo fa il setCurrentGame dentro GameManager
                    
                    System.out.println("--- NUOVA PARTITA ID: " + g.getGameId() + " ---");
                    
                    notifyNewGame(g);

                    try {
                        Thread.sleep(ServerConfig.GAME_DURATION * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    handleGameEnd(g);
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

    private void notifyNewGame(Game g) {
        // La lambda ora funziona perché abbiamo importato ClientSession e SelectionKey
        server.broadcast((session, key) -> {
            session.resetGameStatus(); 
            if (session.isLoggedIn()) {
                ServerResponse resp = new ServerResponse();
                resp.status = "OK";
                resp.message = "NUOVA PARTITA INIZIATA!";
                // Nota: buildGameInfo ora usa GameManager internamente (vedi sotto)
                resp.gameInfo = RequestProcessor.buildGameInfo(g, session);
                return new Gson().toJson(resp);
            }
            return null; 
        });
    }

    private void handleGameEnd(Game g) {
        server.broadcast((session, key) -> {
            if (session.isLoggedIn()) {
                if (!session.isGameFinished()) {
                    UserManager.getInstance().updateStatsTimeOut(session.getUsername());
                }
                
                ServerResponse resp = new ServerResponse();
                resp.status = "EVENT";
                resp.message = "TEMPO SCADUTO";
                resp.isFinished = true;
                resp.solution = new ArrayList<>();
                for (Group gr : g.getGroups()) {
                    resp.solution.add(new ServerResponse.GroupData(gr.getTheme(), gr.getWords()));
                }
                return new Gson().toJson(resp);
            }
            return null;
        });
    }
}