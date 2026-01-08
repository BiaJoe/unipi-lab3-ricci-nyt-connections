package client.logic;

public class Command {
    public final String name;
    public final String alias;
    public final String args;
    public final String description;
    public final CommandHandler handler;

    // Interfaccia funzionale per il codice da eseguire
    @FunctionalInterface
    public interface CommandHandler {
        boolean handle(String[] args) throws Exception;
    }

    public Command(String name, String alias, String args, String description, CommandHandler handler) {
        this.name = name;
        this.alias = alias;
        this.args = args;
        this.description = description;
        this.handler = handler;
    }

    public String getUsage() {
        // Se è il comando speciale "submit" non mostriamo lo slash
        if (name.equals("submit")) return args;
        return "/" + name + (args.isEmpty() ? "" : " " + args);
    }
}

