package server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    public static int PORT;
    
    public static String DATA_FILE_PATH;     // File definizioni partite (read-only)
    public static String USERS_FILE_PATH;    // File utenti
    public static String HISTORY_FILE_PATH;  // NUOVO: File storico partite
    
    public static int GAME_DURATION;
    public static int MAX_ERRORS;
    public static boolean TEST_MODE;

    public static void load(String configFile) throws IOException {
        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);

            PORT = Integer.parseInt(prop.getProperty("port", "8080"));
            TEST_MODE = Boolean.parseBoolean(prop.getProperty("testMode", "false"));

            if (TEST_MODE) {
                System.out.println("!!! MODALITÀ TEST ATTIVA !!!");
                DATA_FILE_PATH = "data/Connections_Test.json";
            } else {
                DATA_FILE_PATH = prop.getProperty("dataFilePath", "data/Connections_Data.json");
            }

            USERS_FILE_PATH = prop.getProperty("usersFilePath", "data/Users.json");
            
            // Default: data/GamesHistory.json se non specificato
            HISTORY_FILE_PATH = prop.getProperty("historyFilePath", "data/GamesHistory.json");

            GAME_DURATION = Integer.parseInt(prop.getProperty("gameDuration", "60"));
            MAX_ERRORS = Integer.parseInt(prop.getProperty("maxErrors", "4"));
        }
    }
}