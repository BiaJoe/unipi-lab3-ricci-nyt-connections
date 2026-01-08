package client.logic;

import client.network.NetworkManager;
import client.ui.ClientRenderer;
import utils.ClientRequest;
import java.io.IOException;
import java.util.*;

public class CommandProcessor {
    private final NetworkManager net;
    private final ClientRenderer ui;
    private final Map<String, Command> commandsList = new LinkedHashMap<>();
    private final Map<String, Command> triggerMap = new HashMap<>();

    public CommandProcessor(NetworkManager net, ClientRenderer ui) {
        this.net = net;
        this.ui = ui;
        initCommands();
    }

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
        
        // HIDDEN
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
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return true;
        String firstToken = parts[0];

        try {
            if (firstToken.startsWith("/")) {
                String key = firstToken.toLowerCase();
                Command cmd = triggerMap.get(key);
                if (cmd != null) {
                    String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                    try { return cmd.handler.handle(args); } 
                    catch (IllegalArgumentException e) { ui.showError(e.getMessage()); }
                } else { ui.showError("Comando sconosciuto: " + firstToken); }
            } else {
                if (parts.length == 4) {
                    net.sendRequest(new ClientRequest.SubmitProposal(Arrays.asList(parts[0], parts[1], parts[2], parts[3])));
                } else { ui.showError("Sintassi errata. Inserisci 4 parole."); }
            }
        } catch (Exception e) { ui.showError("Errore comando: " + e.getMessage()); }
        return true; 
    }

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
    
    // Admin Commands
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