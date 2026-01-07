package server.handlers;

import com.google.gson.Gson;
import utils.ServerResponse;

public class ResponseUtils {
    private static final Gson gson = new Gson();

    // Genera una risposta di Errore standard
    public static String error(String msg, int code) {
        return gson.toJson(new ServerResponse.Error(msg, code));
    }

    // Genera una risposta OK Generica (solo messaggio)
    public static String success(String msg) {
        return gson.toJson(new ServerResponse.Generic(msg));
    }

    // Serializza qualsiasi oggetto ServerResponse (Auth, Stats, ecc.)
    public static String toJson(ServerResponse resp) {
        return gson.toJson(resp);
    }
}