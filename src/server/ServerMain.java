package server;

import server.network.NetworkService;
import server.services.PersistenceService;
import server.ui.ServerLogger;

import java.io.IOException;
import java.util.Scanner;

public class ServerMain {
    private final NetworkService networkService;
    private final PersistenceService persistenceService;
    private GameScheduler gameScheduler;

    public ServerMain() {
        this.networkService = new NetworkService();
        this.persistenceService = new PersistenceService();
    }

    public static void main(String[] args) {
        try {
            // 1. Carica Configurazione
            ServerConfig.load("server.properties");
            
            // 2. Inizializza Managers
            UserManager.getInstance(); 
            GameManager.getInstance(); 
            
            // 3. Avvia Server
            new ServerMain().start();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        // 1. Avvia Servizio Persistenza
        persistenceService.start();

        // 2. Avvia Scheduler Gioco
        gameScheduler = new GameScheduler(networkService);
        new Thread(gameScheduler).start();

        // 3. Avvia Listener Console (exit)
        startConsoleListener();
        
        // 4. Avvia Rete
        networkService.init();  // <--- IMPORTANTE: Inizializza il selector
        networkService.start(); // <--- BLOCCANTE: Il main thread si ferma qui
        
        // Questa riga viene raggiunta solo se networkService.start() termina (es. errore)
        // Ma per l'exit manuale ci pensa il thread console.
        close();
    }

    private void startConsoleListener() {
        new Thread(() -> {
            try (Scanner s = new Scanner(System.in)) {
                while (true) {
                    if (s.hasNextLine() && "exit".equalsIgnoreCase(s.nextLine().trim())) {
                        // Chiudiamo tutto centralmente con close()
                        // Questo terminerà il processo forzatamente
                        close(); 
                        break;
                    }
                }
            } catch (Exception e) {
                ServerLogger.error("Errore console: " + e.getMessage());
            }
        }).start();
    }
    
    private void close() {
        ServerLogger.info("Arresto in corso...");
        
        if (gameScheduler != null) gameScheduler.stop();
        if (networkService != null) networkService.stop();
        if (persistenceService != null) persistenceService.stop();
        
        ServerLogger.info("Server chiuso. Bye!");
        System.exit(0); // Terminazione forzata di tutti i thread
    }
}