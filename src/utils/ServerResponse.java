package utils;

public class ServerResponse {
    public int code;            // Es: 200 (OK), 400 (Errore Client), 500 (Errore Server)
    public String message;      // Messaggio leggibile o descrizione errore
    public Object data;         // Il "payload" variabile (es. la lista di parole, le stats)

    public ServerResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
}