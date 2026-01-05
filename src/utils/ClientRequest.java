package utils;

import java.util.List;

public class ClientRequest {
    public String operation;
    
    // Auth fields
    public String name;
    public String username;
    public String psw;
    public int udpPort; // NUOVO CAMPO
    
    // Update fields
    public String oldName;
    public String newName;
    public String oldPsw;
    public String newPsw;
    
    // Game fields
    public List<String> words;
    public Integer gameId;
}