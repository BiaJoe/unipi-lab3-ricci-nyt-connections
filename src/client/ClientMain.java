package client;

import client.logic.CommandProcessor;
import client.network.NetworkManager;
import client.network.TcpListener;
import client.network.UdpListener;
import client.ui.ConsoleUI;

import java.io.IOException;


/**
 * Main del Client
 * - carica la config
 * - inizializza network e UI 
 * - inizializza il sistema di processing dei comandi
 * - avvia i thread di ascolto
 * - avvia il loop di input da linea di comando
 */
public class ClientMain {
    public static final String CONFIG_FILE = "client.properties";

    public static void main(String[] args) {
        // Carico la Config
        ClientConfig config = new ClientConfig();
        try { config.load(CONFIG_FILE); } catch (IOException e) { 
            System.err.println("Errore config: " + e.getMessage());
            return; 
        }

        ConsoleUI ui = new ConsoleUI(config); // Costruisco la UI, in questo caso la TUI
        NetworkManager net = new NetworkManager(config); // Costruisco il Network Manager

        try {
            net.connect(); // Connessione
            // ui.showMessage("Connesso al server " + config.serverAddress + ":" + config.serverPort);

            // Setup Logica dei comandi
            CommandProcessor commands = new CommandProcessor(net, ui);

            // Avvio Thread di Ascolto su connessione TCP
            new Thread(new TcpListener(net, ui, config.tcpBufferSize)).start();
            
            // Avvio Thread di ricezione notifiche UDP
            new Thread(new UdpListener(net, ui, config.udpBufferSize)).start();

            // Loop di Input
            ui.runInputLoop(commands);

        } catch (IOException e) {
            ui.showError("Impossibile connettersi al server: " + e.getMessage());
        } finally {
            net.close();
            System.out.println("Client chiuso. Alla prossima!");
        }
    }
}