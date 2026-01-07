package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import server.handlers.InfoHandler;
import server.handlers.ResponseUtils;
import server.models.ClientSession;
import server.models.Game;
import server.models.GameMatch; // IMPORTANTE: Usiamo il Match ora
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
            // Riapriamo il file ogni volta per ricominciare il ciclo delle partite
            try (JsonReader reader = new JsonReader(new FileReader(ServerConfig.DATA_FILE_PATH))) {
                reader.beginArray();
                
                while (reader.hasNext() && running) {
                    // 1. Leggiamo il Blueprint (Definizione statica)
                    Game g = gson.fromJson(reader, Game.class);
                    
                    // 2. Impostiamo la partita nel Manager (che crea internamente il GameMatch)
                    GameManager manager = GameManager.getInstance();
                    manager.setCurrentGame(g);
                    
                    // RECUPERIAMO IL MATCH ATTIVO APPENA CREATO
                    GameMatch currentMatch = manager.getCurrentMatch();

                    // 3. Reset sessioni & Binding nuovi stati
                    // Ora colleghiamo la sessione allo stato dentro il GameMatch specifico
                    for (ClientSession session : netService.getAllSessions()) {
                        session.resetGameStatus();
                        if (session.isLoggedIn()) {
                            // Chiediamo al match di creare/recuperare lo stato per questo player
                            PlayerGameState newState = currentMatch.getOrCreatePlayerState(session.getUsername());
                            session.bindState(newState);
                        }
                    }

                    ServerLogger.game("NUOVA PARTITA ID: " + g.getGameId() + " (Run #" + currentMatch.getRunNumber() + ")");
                    
                    // 4. Notifiche Start (UDP + TCP Push)
                    notifyNewGameUDP();
                    // Passiamo il MATCH, non il GAME
                    broadcastTcpGameUpdate(currentMatch); 

                    // 5. Attesa Durata Partita
                    try {
                        Thread.sleep(ServerConfig.GAME_DURATION * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // 6. Fine Partita (Timeout)
                    handleGameEndUDP();
                    // Passiamo il MATCH per mostrare i risultati finali
                    broadcastTcpGameUpdate(currentMatch); 
                }
                ServerLogger.info("Fine file partite. Ricomincio il ciclo.");
                
            } catch (Exception e) {
                if (running) {
                    ServerLogger.error("Errore Scheduler: " + e.getMessage());
                    e.printStackTrace();
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
             // Se loggato e non ha finito (quindi non ha vinto/perso per errori), è timeout.
             if(session.isLoggedIn() && !session.isGameFinished()) {
                 UserManager.getInstance().updateStatsTimeOut(session.getUsername());
             }
        }
        
        ServerResponse.Event event = new ServerResponse.Event("TEMPO SCADUTO");
        event.isFinished = true;
        netService.sendUdpResponse(event);
    }

    // --- AGGIORNAMENTO TCP ---
    // Modificato per accettare GameMatch invece di Game
    private void broadcastTcpGameUpdate(GameMatch match) {
        if (match == null) return;

        for (ClientSession session : netService.getAllSessions()) {
            if (session.isLoggedIn()) {
                // InfoHandler ora vuole GameMatch
                ServerResponse.GameInfoData info = InfoHandler.buildGameInfoData(match, session);
                netService.sendTcpResponse(session, ResponseUtils.toJson(info));
            }
        }
    }
}