# --- VARIABILI ---
JAVAC = javac
JAVA = java
JAR = jar

# Directory
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DATA_DIR = data

# Librerie
# Assicurati che il file esista in lib/
GSON_JAR = $(LIB_DIR)/gson-2.11.0.jar

# Trova tutti i file .java nelle sottocartelle (server, client, utils, handlers, models)
SOURCES := $(shell find $(SRC_DIR) -name "*.java")

# --- TARGET PRINCIPALI ---

all: server_jar client_jar

# 1. Compilazione e Preparazione Fat JAR
# Crea la cartella bin, compila tutto lì dentro e scompatta GSON per includerlo
compile:
	@mkdir -p $(BIN_DIR)
	@echo "[COMPILE] Compilazione sorgenti..."
	@$(JAVAC) -d $(BIN_DIR) -cp $(GSON_JAR) $(SOURCES)
	@echo "[LIB] Inclusione GSON nel pacchetto..."
	@cd $(BIN_DIR) && $(JAR) xf ../$(GSON_JAR)

# 2. Creazione JAR Server
# Crea un jar unico con Entry Point ServerMain
server_jar: compile
	@echo "[JAR] Creazione server.jar..."
	@$(JAR) cfe server.jar server.ServerMain -C $(BIN_DIR) .

# 3. Creazione JAR Client
# Crea un jar unico con Entry Point ClientMain
client_jar: compile
	@echo "[JAR] Creazione client.jar..."
	@$(JAR) cfe client.jar client.ClientMain -C $(BIN_DIR) .

# --- ESECUZIONE ---

runs: server_jar
	@echo "[RUN] Avvio Server..."
	@$(JAVA) -jar server.jar

runc: client_jar
	@echo "[RUN] Avvio Client..."
	@$(JAVA) -jar client.jar

# --- PULIZIA ---

clean:
	@echo "[CLEAN] Rimozione file compilati..."
	@rm -rf $(BIN_DIR)
	@rm -f server.jar client.jar

# Reset Totale (cancella anche il database utenti)
reset: clean
	@echo "[RESET] Cancellazione Database Utenti..."
	@rm -f $(DATA_DIR)/Users.json

.PHONY: all compile server_jar client_jar runs runc clean reset