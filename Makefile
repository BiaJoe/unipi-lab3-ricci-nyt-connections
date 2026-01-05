JAVAC = javac
JAVA = java
JAR = jar
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DATA_DIR = data

# Assicurati che il nome del file corrisponda esattamente a quello in lib/
GSON_JAR = $(LIB_DIR)/gson-2.11.0.jar

# Trova TUTTI i file .java ricorsivamente in src/ (include server/models, client, utils, ecc.)
SOURCES := $(shell find $(SRC_DIR) -name "*.java")

# Default target
all: server_jar client_jar

# 1. Compilazione
compile:
	@mkdir -p $(BIN_DIR)
	@echo "--- Compilazione in corso ---"
	@$(JAVAC) -d $(BIN_DIR) -cp $(GSON_JAR) $(SOURCES)
	@echo "--- Estrazione GSON nel pacchetto ---"
	@cd $(BIN_DIR) && $(JAR) xf ../$(GSON_JAR)

# 2. Creazione JAR Server
server_jar: compile
	@echo "--- Creazione server.jar ---"
	@$(JAR) cfe server.jar server.ServerMain -C $(BIN_DIR) .

# 3. Creazione JAR Client
client_jar: compile
	@echo "--- Creazione client.jar ---"
	@$(JAR) cfe client.jar client.ClientMain -C $(BIN_DIR) .

# --- Comandi di Esecuzione Rapida ---
runs: server_jar
	@echo "Avvio Server..."
	@$(JAVA) -jar server.jar

runc: client_jar
	@echo "Avvio Client..."
	@$(JAVA) -jar client.jar

# --- Pulizia ---
clean:
	@rm -rf $(BIN_DIR)
	@rm -f server.jar client.jar
	@echo "Pulizia completata."

# Reset Totale (inclusi utenti)
reset: clean
	@rm -f $(DATA_DIR)/Users.json
	@echo "Reset completato (Database utenti eliminato)."

.PHONY: all compile server_jar client_jar runs runc clean reset