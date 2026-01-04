package utils;

import java.util.List;

// Questa classe rappresenta il messaggio che il CLIENT invia al SERVER
public class ClientRequest {
    public String operation;
    
    public String username;
    public String password;
    public Integer gameId;      // Integer, può essere null se non serve
    public List<String> words;  // La lista delle 4 parole per la proposta

    // Costruttore vuoto necessario a GSON per ricostruire l'oggetto
    public ClientRequest() {}

    // Costruttore di comodo per i test
    public ClientRequest(String operation) {
        this.operation = operation;
    }
}
