# --- VARIABILI ---
JAVAC = javac
JAVA = java
JAR = jar

# Directory del progetto
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DATA_DIR = data

# Libreria GSON (Assicurati che il nome file coincida con quello in lib/)
GSON_JAR = $(LIB_DIR)/gson-2.11.0.jar

# Trova automaticamente tutti i sorgenti (inclusi i nuovi in client/ui)
SOURCES := $(shell find $(SRC_DIR) -name "*.java")

# --- TARGET PRINCIPALI ---

all: server_jar client_jar

# 1. Compilazione e Preparazione Fat JAR
# Crea la cartella bin, compila i sorgenti e scompatta GSON per includerlo nel jar finale
compile:
	@mkdir -p $(BIN_DIR)
	@echo "[COMPILE] Trovati $(words $(SOURCES)) file sorgente."
	@echo "[COMPILE] Compilazione sorgenti..."
	@$(JAVAC) -d $(BIN_DIR) -cp $(GSON_JAR) $(SOURCES)
	@echo "[LIB] Integrazione GSON nel pacchetto..."
	@cd $(BIN_DIR) && $(JAR) xf ../$(GSON_JAR)

# 2. Creazione JAR Server
# Entry Point: server.ServerMain
server_jar: compile
	@echo "[JAR] Creazione server.jar..."
	@$(JAR) cfe server.jar server.ServerMain -C $(BIN_DIR) .

# 3. Creazione JAR Client
# Entry Point: client.ClientMain
client_jar: compile
	@echo "[JAR] Creazione client.jar..."
	@$(JAR) cfe client.jar client.ClientMain -C $(BIN_DIR) .

# --- ESECUZIONE ---

# Avvia il Server
runs: server_jar
	@echo "[RUN] Avvio Server..."
	@$(JAVA) -jar server.jar

# Avvia il Client (Interfaccia CLI)
runc: client_jar
	@echo "[RUN] Avvio Client..."
	@$(JAVA) -jar client.jar

# --- PULIZIA E RESET ---

# Rimuove i file compilati e i jar
clean:
	@echo "[CLEAN] Rimozione file compilati..."
	@rm -rf $(BIN_DIR)
	@rm -f server.jar client.jar

# Rimuove tutto + il database utenti (Reset totale)
reset: clean
	@echo "[RESET] Cancellazione Database Utenti..."
	@rm -f $(DATA_DIR)/Users.json

.PHONY: all compile server_jar client_jar runs runc clean reset