package server;

import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static final int PORT = 8080;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SERVER CONNECTIONS AVVIATO SU PORTA " + PORT);
            System.out.println("In attesa di giocatori...");

            // Loop infinito che accetta clienti
            while (true) {
                // 1. Il Main Thread aspetta una connessione
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuovo client connesso: " + clientSocket.getInetAddress());
                
                // 2. Invece di gestire la richiesta qui, creiamo un Thread dedicato
                // Passiamo il socket al ClientHandler
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread clientThread = new Thread(handler);
                
                // 3. Avviamo il thread (questo chiama il metodo run() di ClientHandler)
                clientThread.start();
                
                // Il loop ricomincia subito ed è pronto per il prossimo client
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}