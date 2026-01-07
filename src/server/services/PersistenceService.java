package server.services;

import server.GameManager;
import server.UserManager;
import server.ui.ServerLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PersistenceService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int SAVE_INTERVAL_SECONDS = 30; // Salva ogni 30 secondi

    public void start() {
        // 1. Avvia il salvataggio periodico
        scheduler.scheduleAtFixedRate(this::saveAll, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // 2. Aggiunge un "Shutdown Hook": codice che viene eseguito se premi CTRL+C o chiudi il server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServerLogger.info("Chiusura rilevata: Salvataggio dati in corso...");
            saveAll();
            ServerLogger.info("Dati salvati. Bye!");
        }));
        
        ServerLogger.info("Servizio di Persistenza avviato (Intervallo: " + SAVE_INTERVAL_SECONDS + "s)");
    }

    // Metodo unico per salvare tutto
    private void saveAll() {
        try {
            UserManager.getInstance().saveData();
            GameManager.getInstance().saveData();
            // ServerLogger.info("Salvataggio periodico completato."); // Decommenta se vuoi spam
        } catch (Exception e) {
            ServerLogger.error("Errore durante il salvataggio periodico: " + e.getMessage());
        }
    }

    public void stop() {
        saveAll();
        scheduler.shutdown();
    }
}