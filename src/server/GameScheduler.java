package server;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import server.handlers.ResponseUtils; 
import server.models.ClientSession;
import server.models.Game;
import server.models.GameMatch;
import server.models.PlayerGameState;
import server.network.NetworkService;
import server.services.GameManager;
import server.services.UserManager;
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
                    // Leggo il Blueprint dal JSON
                    Game g = gson.fromJson(reader, Game.class);
                    
                    // Imposto la partita nel Manager
                    GameManager manager = GameManager.getInstance();
                    manager.setCurrentGame(g);
                    GameMatch currentMatch = manager.getCurrentMatch();

                    ServerLogger.game("NUOVA PARTITA ID: " + g.getGameId() + " (Run #" + currentMatch.getRunNumber() + ")");

                    // Inizializzazione Giocatori Connessi
                    for (ClientSession session : netService.getAllSessions()) {
                        if (session.isLoggedIn()) {
                            currentMatch.getOrCreatePlayerState(session.getUsername());
                        }
                    }
                    
                    // Notifiche Start
                    notifyNewGameUDP();
                    broadcastTcpGameUpdate(currentMatch); 

                    // Attesa Durata Partita
                    try {
                        Thread.sleep(ServerConfig.GAME_DURATION * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // Fine Partita (Timeout)
                    handleGameEndUDP(currentMatch); // Passiamo il match per controllare chi ha finito
                    broadcastTcpGameUpdate(currentMatch); // Aggiornamento finale con risultati
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

    private void handleGameEndUDP(GameMatch match) {
        // Aggiorno le statistiche per chi è andato in Timeout
        if (match != null) {
            for(ClientSession session : netService.getAllSessions()) {
                if(session.isLoggedIn()) {
                    PlayerGameState state = match.getPlayerState(session.getUsername());
                    // Se lo stato esiste e NON è finito, allora è un timeout
                    if (state != null && !state.isFinished()) {
                        UserManager.getInstance().updateStatsTimeOut(session.getUsername());
                        // Segniamo come finito nello stato locale per coerenza
                        state.setFinished(true); 
                    }
                }
            }
        }
        
        ServerResponse.Event event = new ServerResponse.Event("TEMPO SCADUTO");
        event.isFinished = true;
        netService.sendUdpResponse(event);
    }

    // AGGIORNAMENTO TCP 
    private void broadcastTcpGameUpdate(GameMatch match) {
        if (match == null) return;

        for (ClientSession session : netService.getAllSessions()) {
            if (session.isLoggedIn()) {
                // Recupero lo stato da match
                PlayerGameState pState = match.getOrCreatePlayerState(session.getUsername());
                
                // Uso ResponseUtils per costruire il JSON
                ServerResponse.GameInfoData info = ResponseUtils.buildGameInfo(match, pState);
                
                netService.sendTcpResponse(session, ResponseUtils.toJson(info));
            }
        }
    }
}