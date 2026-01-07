package client;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ClientConfig {
    public String serverAddress;
    public int serverPort;
    public int connectTimeout;
    public int tcpBufferSize;
    public int udpBufferSize;
    public int hudColumn;
    
    // Nuove proprietà per i file
    public String trophyFile;
    public String helpFile;      
    public int syncIntervalSeconds;

    public void load(String filePath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(filePath)) {
            props.load(in);
        }

        this.serverAddress = props.getProperty("serverAddress", "127.0.0.1");
        this.serverPort = Integer.parseInt(props.getProperty("serverPort", "8080"));
        this.connectTimeout = Integer.parseInt(props.getProperty("connectTimeout", "5000"));
        
        this.tcpBufferSize = Integer.parseInt(props.getProperty("tcpBufferSize", "8192"));
        this.udpBufferSize = Integer.parseInt(props.getProperty("udpBufferSize", "4096"));
        
        this.hudColumn = Integer.parseInt(props.getProperty("hudColumn", "80"));
        
        this.trophyFile = props.getProperty("trophyFile", "decorations/trophy.txt");
        this.helpFile = props.getProperty("helpFile", "decorations/help.txt"); // <--- AGGIUNTO DEFAULT
        
        this.syncIntervalSeconds = Integer.parseInt(props.getProperty("syncIntervalSeconds", "10"));
    }
}