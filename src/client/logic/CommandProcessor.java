package client.logic;

import client.network.NetworkManager;
import client.ui.ClientRenderer;
import utils.ClientRequest;

import java.io.IOException;
import java.util.*;

public class CommandProcessor {
    private final NetworkManager net;
    private final ClientRenderer ui;
    
    // Mappe per gestione interna
    private final Map<String, Command> commandsList = new LinkedHashMap<>(); // Per l'Help (ordine inserimento)
    private final Map<String, Command> triggerMap = new HashMap<>();         // Per esecuzione (alias inclusi)

    public CommandProcessor(NetworkManager net, ClientRenderer ui) {
        this.net = net;
        this.ui = ui;
        initCommands();
    }

    // =========================================================================================
    //                                  TABELLA DI CONFIGURAZIONE
    // =========================================================================================
    private void initCommands() {
        //  COMANDO        ALIAS   ARGOMENTI                 DESCRIZIONE                   CODICE DA ESEGUIRE
        //  -------------  -----   ------------------------  ----------------------------  --------------------
        cmd("register",    "r",    "<user> <pass>",          "Registra un nuovo utente",   this::doRegister);
        cmd("login",       "l",    "<user> <pass>",          "Accedi al gioco",            this::doLogin);
        cmd("logout",      "out",  "",                       "Disconnetti utente",         this::doLogout);
        cmd("update_creds","u",    "<u1> <u2> <p1> <p2>",    "Modifica credenziali",       this::doUpdateCreds);
        
        cmd("info",        "i",    "",                       "Mostra stato partita",       this::doInfo);
        cmd("gameinfo",    "gi",   "<id>",                   "Mostra partita passata",     this::doGameInfo);
        cmd("me",          "m",    "",                       "Le tue statistiche",         this::doMe);
        cmd("rank",        "rk",   "",                       "Classifica giocatori",       this::doRank);
        
        cmd("help",        "h",    "",                       "Mostra questa lista",        this::doHelp);
        cmd("exit",        "e",    "",                       "Chiudi il client",           this::doExit);

        // Caso speciale: submit non ha trigger slash, ma lo mettiamo qui per l'Help
        cmd("submit",      "",     "<w1> <w2> <w3> <w4>",    "Invia tentativo",            this::doSubmitFake);
    }
    // =========================================================================================

    /**
     * Helper per registrare i comandi in modo compatto
     */
    private void cmd(String name, String alias, String args, String desc, Command.CommandHandler handler) {
        Command c = new Command(name, alias, args, desc, handler);
        commandsList.put(name, c);
        triggerMap.put("/" + name, c);
        if (!alias.isEmpty()) triggerMap.put("/" + alias, c);
    }

    // --- LOOP DI PROCESSO ---

    public boolean processInput(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return true;

        String firstToken = parts[0];

        try {
            // A. COMANDI (Iniziano con /)
            if (firstToken.startsWith("/")) {
                String key = firstToken.toLowerCase();
                Command cmd = triggerMap.get(key);

                if (cmd != null) {
                    String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                    try {
                        return cmd.handler.handle(args);
                    } catch (IllegalArgumentException e) {
                        ui.showError(e.getMessage());
                    }
                } else {
                    ui.showError("Comando sconosciuto: " + firstToken);
                }
            } 
            // B. GIOCO (4 parole dirette)
            else {
                if (parts.length == 4) {
                    net.sendRequest(new ClientRequest.SubmitProposal(Arrays.asList(parts[0], parts[1], parts[2], parts[3])));
                } else {
                    ui.showError("Sintassi errata. Inserisci 4 parole per giocare o usa /help.");
                }
            }
        } catch (Exception e) {
            ui.showError("Errore esecuzione comando: " + e.getMessage());
        }

        return true; // Default: continua
    }

    // =========================================================================================
    //                                  IMPLEMENTAZIONE CODICI
    // =========================================================================================

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

    private boolean doLogout(String[] args) throws IOException {
        net.sendRequest(new ClientRequest.Logout());
        return true;
    }

    private boolean doUpdateCreds(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("Usa: /update_creds <oldU> <newU> <oldP> <newP>");
        net.sendRequest(new ClientRequest.UpdateCredentials(args[0], args[1], args[2], args[3]));
        return true;
    }

    private boolean doInfo(String[] args) throws IOException {
        net.sendRequest(new ClientRequest.GameInfo(0));
        return true;
    }

    private boolean doGameInfo(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usa: /gameinfo <id>");
        try {
            int id = Integer.parseInt(args[0]);
            net.sendRequest(new ClientRequest.GameInfo(id));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("L'ID deve essere un numero intero.");
        }
        return true;
    }

    private boolean doMe(String[] args) throws IOException {
        net.sendRequest(new ClientRequest.PlayerStats());
        return true;
    }

    private boolean doRank(String[] args) throws IOException {
        net.sendRequest(new ClientRequest.Leaderboard(0));
        return true;
    }

    private boolean doHelp(String[] args) {
        ui.showHelp(commandsList.values());
        return true;
    }

    private boolean doExit(String[] args) {
        return false; // Torna false per chiudere il loop
    }

    private boolean doSubmitFake(String[] args) {
        throw new IllegalArgumentException("Non usare /submit. Scrivi semplicemente le 4 parole separate da spazio.");
    }
}