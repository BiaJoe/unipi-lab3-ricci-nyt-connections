package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import server.handlers.InfoHandler; // Serve per generare il JSON di aggiornamento
import server.handlers.ResponseUtils;
import server.models.ClientSession;
import server.models.Game;
import server.models.PlayerGameState;
import server.network.NetworkService;
import server.ui.ServerLogger;
import utils.ServerResponse;

import java.io.FileReader;

public class GameScheduler implements Runnable {
    private final NetworkService netService;
    private volatile boolean running = true;
    private final Gson gson = new Gson();

    public GameScheduler(NetworkService netService) {
        this.netService = netService;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        ServerLogger.info("Avvio Game Scheduler...");

        while (running) {
            try (JsonReader reader = new JsonReader(new FileReader(ServerConfig.DATA_FILE_PATH))) {
                reader.beginArray();
                
                while (reader.hasNext() && running) {
                    Game g = gson.fromJson(reader, Game.class);
                    
                    // 1. Imposta Nuova Partita
                    GameManager.getInstance().setCurrentGame(g);
                    
                    // 2. Reset sessioni & Binding nuovi stati
                    for (ClientSession session : netService.getAllSessions()) {
                        session.resetGameStatus();
                        if (session.isLoggedIn()) {
                            PlayerGameState newState = GameManager.getInstance().getOrCreatePlayerState(session.getUsername());
                            session.bindState(newState);
                        }
                    }

                    ServerLogger.game("NUOVA PARTITA ID: " + g.getGameId());
                    
                    // 3. Notifiche Start (UDP + TCP Push)
                    notifyNewGameUDP();
                    broadcastTcpGameUpdate(g); // <--- FIX 3: Aggiorna automaticamente la UI di tutti

                    // 4. Attesa Durata Partita
                    try {
                        Thread.sleep(ServerConfig.GAME_DURATION * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // 5. Fine Partita (Timeout)
                    handleGameEndUDP();
                    broadcastTcpGameUpdate(g); // <--- FIX 2: Mostra i risultati finali a tutti
                }
                ServerLogger.info("Fine file partite. Ricomincio.");
                
            } catch (Exception e) {
                if (running) {
                    ServerLogger.error("Errore Scheduler: " + e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ie) {}
                }
            }
        }
    }

    private void notifyNewGameUDP() {
        ServerResponse.Event event = new ServerResponse.Event("NUOVA PARTITA INIZIATA!");
        netService.sendUdpResponse(event);
    }

    private void handleGameEndUDP() {
        for(ClientSession session : netService.getAllSessions()) {
             if(session.isLoggedIn() && !session.isGameFinished()) {
                 UserManager.getInstance().updateStatsTimeOut(session.getUsername());
             }
        }
        
        ServerResponse.Event event = new ServerResponse.Event("TEMPO SCADUTO");
        event.isFinished = true;
        netService.sendUdpResponse(event);
    }

    // --- NUOVO METODO PER IL REFRESH AUTOMATICO ---
    private void broadcastTcpGameUpdate(Game g) {
        for (ClientSession session : netService.getAllSessions()) {
            if (session.isLoggedIn()) {
                // Genera la scheda aggiornata personalizzata per l'utente (es. parole mescolate)
                ServerResponse.GameInfoData info = InfoHandler.buildGameInfoData(g, session);
                // Invia direttamente via TCP
                netService.sendTcpResponse(session, ResponseUtils.toJson(info));
            }
        }
    }
}