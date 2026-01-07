package utils;

public class ResponseCodes {
    // --- SUCCESSO ---
    public static final int OK = 200;

    // --- ERRORI CLIENT (4xx) ---
    public static final int BAD_REQUEST = 400;           // Sintassi errata / Dati mancanti
    public static final int UNAUTHORIZED = 401;          // Non loggato / Credenziali errate
    public static final int FORBIDDEN = 403;             // Operazione non permessa
    public static final int NOT_FOUND = 404;             // Partita/Oggetto non trovato
    public static final int ALREADY_LOGGED_IN = 405;     // Utente già connesso altrove
    public static final int TIMEOUT = 408;               // Tempo scaduto
    public static final int GAME_FINISHED = 409;         // Partita già conclusa per l'utente
    
    // Errori specifici di Logica Gioco
    public static final int INVALID_WORDS = 410;         // Parole non presenti nella griglia
    public static final int DUPLICATE_GUESS = 411;       // Parola già indovinata in un altro gruppo

    // --- ERRORI SERVER (5xx) ---
    public static final int INTERNAL_SERVER_ERROR = 500; // Errore generico / Partita non attiva
}