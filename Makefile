# --- VARIABILI ---
JAVAC = javac
JAVA = java
JAR = jar

# Directory del progetto
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DATA_DIR = data

# Libreria GSON
GSON_JAR = $(LIB_DIR)/gson-2.11.0.jar

# Trova automaticamente tutti i sorgenti
SOURCES := $(shell find $(SRC_DIR) -name "*.java")

# --- TARGET PRINCIPALI ---

all: server_jar client_jar

# 1. Compilazione
compile:
	@mkdir -p $(BIN_DIR)
	@echo "[COMPILE] Trovati $(words $(SOURCES)) file sorgente."
	@echo "[COMPILE] Compilazione sorgenti..."
	@$(JAVAC) -d $(BIN_DIR) -cp $(GSON_JAR) $(SOURCES)
	@echo "[LIB] Integrazione GSON nel pacchetto..."
	@cd $(BIN_DIR) && $(JAR) xf ../$(GSON_JAR)

# 2. Creazione JAR Server
server_jar: compile
	@echo "[JAR] Creazione server.jar..."
	@$(JAR) cfe server.jar server.ServerMain -C $(BIN_DIR) .

# 3. Creazione JAR Client
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

# --- PULIZIA E RESET ---

clean:
	@echo "[CLEAN] Rimozione file compilati..."
	@rm -rf $(BIN_DIR)
	@rm -f server.jar client.jar

# Target RESET Sicuro:
# Cancella SOLO i file generati automaticamente (Utenti e Storico).
# NON tocca Connections_Data.json o Connections_Test.json (Dati Fondamentali).
reset: clean
	@echo "[RESET] Cancellazione file generati (Users e History)..."
	@rm -f $(DATA_DIR)/Users.json
	@rm -f $(DATA_DIR)/GamesHistory.json

.PHONY: all compile server_jar client_jar runs runc clean reset