package client;

import client.logic.CommandProcessor;
import client.network.NetworkManager;
import client.network.TcpListener;
import client.network.UdpListener;
import client.ui.ConsoleUI;

import java.io.IOException;

public class ClientMain {
    public static final String CONFIG_FILE = "client.properties";

    public static void main(String[] args) {
        // 1. Caricamento Configurazione
        ClientConfig config = new ClientConfig();
        try { config.load(CONFIG_FILE); } catch (IOException e) { 
            System.err.println("Errore config: " + e.getMessage());
            return; 
        }

        // 2. Setup UI
        ConsoleUI ui = new ConsoleUI(config.helpFile, config.trophyFile);
        
        // 3. Setup Network
        NetworkManager net = new NetworkManager(config);

        try {
            // 4. Connessione
            net.connect();
            ui.init(); // Stampa banner
            ui.showMessage("Connesso al server " + config.serverAddress + ":" + config.serverPort);

            // 5. Setup Logic (CommandProcessor)
            // Nota: CommandProcessor usa (NetworkManager, ClientRenderer)
            CommandProcessor commands = new CommandProcessor(net, ui);

            // 6. Avvio Thread di Ascolto
            // TcpListener vuole (net, ui, bufferSize)
            new Thread(new TcpListener(net, ui, config.tcpBufferSize)).start();
            
            // UdpListener vuole (net, ui, bufferSize)
            new Thread(new UdpListener(net, ui, config.udpBufferSize)).start();

            // NOTA: SyncAndRenderThread è stato rimosso perché la UI è a eventi ora.

            // 7. Loop di Input
            // Deleghiamo alla UI la gestione del loop di lettura per mantenere il main pulito
            // e gestire correttamente il prompt "> "
            ui.runInputLoop(commands);

        } catch (IOException e) {
            ui.showError("Impossibile connettersi al server: " + e.getMessage());
        } finally {
            net.close();
            System.out.println("Client chiuso.");
        }
    }
}