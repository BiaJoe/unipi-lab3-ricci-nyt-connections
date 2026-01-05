package server.handlers;

import com.google.gson.Gson;
import utils.ServerResponse;

public class ResponseUtils {
    private static final Gson gson = new Gson();

    public static String error(String msg, int code) {
        ServerResponse r = new ServerResponse();
        r.status = "ERROR";
        r.message = msg;
        r.errorCode = code;
        return gson.toJson(r);
    }

    public static String success(String msg) {
        ServerResponse r = new ServerResponse();
        r.status = "OK";
        r.message = msg;
        return gson.toJson(r);
    }

    // Helper per convertire qualsiasi oggetto risposta in JSON
    public static String toJson(ServerResponse resp) {
        return gson.toJson(resp);
    }
}