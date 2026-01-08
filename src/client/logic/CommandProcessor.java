package client.logic;

import client.network.NetworkManager;
import client.ui.ClientRenderer;
import utils.ClientRequest;
import java.io.IOException;
import java.util.*;

/**
 * cuore logico del client
 * permette di definire comandi e associarvi un metodo
 * i comandi contengono delle stringhe 
 * che la UI può usare per il tutorial /help
 */
public class CommandProcessor {
    private final NetworkManager net;
    private final ClientRenderer ui;
    private final Map<String, Command> commandsList = new LinkedHashMap<>(); // contiene i comandi e i loro nomi
    private final Map<String, Command> triggerMap = new HashMap<>(); // contiene i comandi in formato trigger /comando

    public CommandProcessor(NetworkManager net, ClientRenderer ui) {
        this.net = net;
        this.ui = ui;
        initCommands();
    }

    // 0gni comando ha nome, alias per brevità, argomenti, descrizione (per tutorial)
    private void initCommands() {
        cmd("register",    "r",    "<user> <pass>",          "Registra un nuovo utente",   this::doRegister);
        cmd("login",       "l",    "<user> <pass>",          "Accedi al gioco",            this::doLogin);
        cmd("logout",      "out",  "",                       "Disconnetti utente",         this::doLogout);
        cmd("update_creds","u",    "<u1> <u2> <p1> <p2>",    "Modifica credenziali",       this::doUpdateCreds);
        cmd("info",        "i",    "",                       "Stato TUA partita corrente", this::doInfo);
        cmd("gameinfo",    "gi",   "<id>",                   "Stato partita storica",      this::doGameInfo);
        cmd("gamestats",   "gs",   "[id]",                   "Statistiche GLOBALI partita",this::doGameStats); 
        cmd("me",          "m",    "",                       "Le tue statistiche totali",  this::doMe);
        cmd("rank",        "rk",   "[K | user]",             "Classifica (Top K o Utente)",this::doRank);
        cmd("help",        "h",    "",                       "Mostra questa lista",        this::doHelp);
        cmd("exit",        "e",    "",                       "Chiudi il client",           this::doExit);
        
        // Comandi nascosti, il submit è sostituito dalla scrittura di 4 parole
        // Gli altri sono per gli ADMIN
        hiddenCmd("submit", "", "", this::doSubmitFake);
        hiddenCmd("oracle", "<psw>", "Rivela soluzione", this::doOracle);
        hiddenCmd("god",    "<psw>", "Rivela utenti",    this::doGod);
    }

    private void cmd(String name, String alias, String args, String desc, Command.CommandHandler handler) {
        Command c = new Command(name, alias, args, desc, handler);
        commandsList.put(name, c);
        triggerMap.put("/" + name, c);
        if (!alias.isEmpty()) triggerMap.put("/" + alias, c);
    }

    private void hiddenCmd(String name, String args, String desc, Command.CommandHandler handler) {
        Command c = new Command(name, "", args, desc, handler);
        triggerMap.put("/" + name, c);
    }

    public boolean processInput(String line) {
        // divido le parole usando il parser che rispetta le virgolette
        List<String> tokens = parseArgs(line.trim());
        if (tokens.isEmpty()) return true;
        
        String[] parts = tokens.toArray(new String[0]);
        String firstToken = parts[0];

        try {
            // gestisco i comandi trigger /...
            if (firstToken.startsWith("/")) {
                String key = firstToken.toLowerCase();
                Command cmd = triggerMap.get(key);
                if (cmd != null) {
                    String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                    try { return cmd.handler.handle(args); } 
                    catch (IllegalArgumentException e) { ui.showError(e.getMessage()); }
                } else { ui.showError("Comando sconosciuto: " + firstToken); }

            // gestisco le parole proposte e le invio al server
            } else {
                if (parts.length == 4) {
                    net.sendRequest(new ClientRequest.SubmitProposal(Arrays.asList(parts[0], parts[1], parts[2], parts[3])));
                } else { ui.showError("Sintassi errata. Inserisci 4 parole."); }
            }
        } catch (Exception e) { ui.showError("Errore comando: " + e.getMessage()); }
        return true; 
    }

    // Metodo helper per tokenizzare rispettando le virgolette "PER PAROLE MULTIPLE"
    private List<String> parseArgs(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes; 
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    // azioni legate ai comandi
    // controllano la validità del comando
    // eseguono la richiesta tramite network al server
    // ritorno true se tutto può continuare 

    private boolean doRegister(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usa: /register <user> <pass>");
        net.sendRequest(new ClientRequest.Register(args[0], args[1]));
        return true;
    }
    private boolean doLogin(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usa: /login <user> <pass>");
        net.sendRequest(new ClientRequest.Login(args[0], args[1], net.getLocalUdpPort()));
        return true;
    }
    private boolean doLogout(String[] args) throws IOException { net.sendRequest(new ClientRequest.Logout()); return true; }
    private boolean doUpdateCreds(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("Usa: /update_creds <oldU> <newU> <oldP> <newP>");
        net.sendRequest(new ClientRequest.UpdateCredentials(args[0], args[1], args[2], args[3]));
        return true;
    }
    
    // MODIFICATO: Usa il costruttore vuoto per chiedere info sulla partita corrente (null)
    private boolean doInfo(String[] args) throws IOException { 
        net.sendRequest(new ClientRequest.GameInfo()); 
        return true; 
    }

    private boolean doGameInfo(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usa: /gameinfo <id>");
        try { net.sendRequest(new ClientRequest.GameInfo(Integer.parseInt(args[0]))); } 
        catch (NumberFormatException e) { throw new IllegalArgumentException("ID non valido"); }
        return true;
    }
    
    // MODIFICATO: Gestisce sia nessun argomento (corrente) sia ID specifico
    private boolean doGameStats(String[] args) throws IOException {
        if (args.length == 0) {
            // Nessun ID -> Partita Corrente
            net.sendRequest(new ClientRequest.RequestGameStats());
        } else {
            // ID specificato (es. 0) -> Partita Specifica
            try { 
                int id = Integer.parseInt(args[0]);
                net.sendRequest(new ClientRequest.RequestGameStats(id));
            } catch (NumberFormatException e) { 
                throw new IllegalArgumentException("ID non valido"); 
            }
        }
        return true;
    }

    private boolean doMe(String[] args) throws IOException { net.sendRequest(new ClientRequest.PlayerStats()); return true; }
    private boolean doRank(String[] args) throws IOException {
        if (args.length == 0) net.sendRequest(new ClientRequest.Leaderboard());
        else {
            try { net.sendRequest(new ClientRequest.Leaderboard(Integer.parseInt(args[0]))); } 
            catch (NumberFormatException e) { net.sendRequest(new ClientRequest.Leaderboard(args[0])); }
        }
        return true;
    }
    
    // Comandi dell'admin
    private boolean doOracle(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usa: /oracle <admin_password>");
        net.sendRequest(new ClientRequest.Oracle(args[0]));
        return true;
    }
    private boolean doGod(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usa: /god <admin_password>");
        net.sendRequest(new ClientRequest.God(args[0]));
        return true;
    }

    private boolean doHelp(String[] args) { ui.showHelp(commandsList.values()); return true; }
    private boolean doExit(String[] args) { return false; }
    private boolean doSubmitFake(String[] args) { throw new IllegalArgumentException("Non usare /submit. Scrivi le 4 parole."); }
}