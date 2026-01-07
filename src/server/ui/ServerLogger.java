package server.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

public class ServerLogger {
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- COLORI ANSI ---
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m"; // INFO
    private static final String RED    = "\u001B[31m"; // ERROR
    private static final String BLUE   = "\u001B[34m"; // GAME / NUMERI
    
    // Colori JSON / Traffico
    private static final String CYAN   = "\u001B[36m"; // Header OUT
    private static final String PURPLE = "\u001B[35m"; // Header IN
    private static final String YELLOW = "\u001B[33m"; // Header UDP / Chiavi
    private static final String WHITE  = "\u001B[37m"; // Valori JSON

    // --- HELPER FUNCTION PER IL TAG COLORATO ---
    // Colora tutto il blocco [ORARIO LABEL]
    private static String getColoredTag(String color, String label) {
        return String.format("%s[%s %s]%s", color, timestamp(), label, RESET);
    }

    // --- LOGGING STANDARD ---

    public static void info(String msg) {
        // Esempio: [19:30:00 INFO] Messaggio (Tutto il tag è Verde)
        System.out.println(getColoredTag(GREEN, "INFO") + " " + msg);
    }

    public static void error(String msg) {
        // Esempio: [19:30:00 ERROR] Messaggio (Tutto il tag è Rosso)
        System.out.println(getColoredTag(RED, "ERROR") + " " + msg);
    }

    public static void game(String msg) {
        // Esempio: [19:30:00 GAME] Messaggio (Tutto il tag è Blu)
        System.out.println(getColoredTag(BLUE, "GAME") + " " + msg);
    }

    // --- LOGGING TRAFFICO ---

    public static void logTraffic(String header, String rawJson) {
        String color = CYAN; // Default OUT
        if (header.contains("IN")) color = PURPLE;
        else if (header.contains("UDP")) color = YELLOW;

        // Pulizia header da eventuali parentesi extra
        String cleanHeader = header.replace("[", "").replace("]", "").trim();

        String formattedJson;
        try {
            JsonElement je = JsonParser.parseString(rawJson);
            StringBuilder sb = new StringBuilder();
            formatJsonCustom(sb, je, "");
            formattedJson = sb.toString();
        } catch (Exception e) {
            formattedJson = rawJson;
        }

        // Stampa il tag colorato e a capo il JSON
        System.out.println(getColoredTag(color, cleanHeader) + "\n" + formattedJson);
    }

    // --- FORMATTAZIONE JSON (Liste Orizzontali) ---

    private static void formatJsonCustom(StringBuilder sb, JsonElement el, String indent) {
        if (el.isJsonPrimitive()) {
            String val = el.getAsString();
            if (el.getAsJsonPrimitive().isNumber() || el.getAsJsonPrimitive().isBoolean()) {
                sb.append(BLUE).append(val).append(RESET);
            } else {
                sb.append(WHITE).append("\"").append(val).append("\"").append(RESET);
            }

        } else if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            sb.append("{\n"); 
            
            Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
            int count = 0;
            for (Map.Entry<String, JsonElement> entry : entries) {
                sb.append(indent).append("  ");
                sb.append(YELLOW).append("\"").append(entry.getKey()).append("\"").append(RESET).append(": ");
                
                formatJsonCustom(sb, entry.getValue(), indent + "  ");
                
                if (++count < entries.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append(indent).append("}");

        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            
            if (isSimpleArray(arr)) {
                sb.append("[ ");
                for (int i = 0; i < arr.size(); i++) {
                    formatJsonCustom(sb, arr.get(i), "");
                    if (i < arr.size() - 1) sb.append(", ");
                }
                sb.append(" ]");
            } else {
                sb.append("[\n");
                for (int i = 0; i < arr.size(); i++) {
                    sb.append(indent).append("  ");
                    formatJsonCustom(sb, arr.get(i), indent + "  ");
                    if (i < arr.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append(indent).append("]");
            }

        } else if (el.isJsonNull()) {
            sb.append("null");
        }
    }

    private static boolean isSimpleArray(JsonArray arr) {
        if (arr.size() == 0) return true;
        for (JsonElement e : arr) {
            if (e.isJsonObject() || e.isJsonArray()) return false;
        }
        return true;
    }

    public static void logJsonReceived(String source, String json) {
        String cleanSource = source.startsWith("/") ? source.substring(1) : source;
        logTraffic("TCP IN <- " + cleanSource, json);
    }

    private static String timestamp() {
        return LocalTime.now().format(dtf);
    }
}