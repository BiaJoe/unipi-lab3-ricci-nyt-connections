package server.services;

import server.ui.ServerLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PersistenceService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int SAVE_INTERVAL_SECONDS = 30; // Salva ogni 30 secondi

    public void start() {
        // Avvio salvataggio periodico
        scheduler.scheduleAtFixedRate(this::saveAll, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServerLogger.info("Chiusura rilevata: Salvataggio dati in corso...");
            saveAll();
            ServerLogger.info("Dati salvati. Bye!");
        }));
        
        ServerLogger.info("Servizio di Persistenza avviato (Intervallo: " + SAVE_INTERVAL_SECONDS + "s)");
    }

    // Metodo unico per salvare 
    private void saveAll() {
        try {
            UserManager.getInstance().saveData();
            GameManager.getInstance().saveData();
            ServerLogger.info("Salvataggio periodico completato.");
        } catch (Exception e) {
            ServerLogger.error("Errore durante il salvataggio periodico: " + e.getMessage());
        }
    }

    public void stop() {
        saveAll();
        scheduler.shutdown();
    }
}