JAVAC = javac
JAVA = java
SRC_DIR = src
BIN_DIR = bin
LIB_DIR = lib
DATA_DIR = data

GSON_JAR = $(LIB_DIR)/gson-2.11.0.jar
CLASSPATH = $(BIN_DIR):$(GSON_JAR)

SOURCES = $(SRC_DIR)/server/*.java \
          $(SRC_DIR)/client/*.java \
          $(SRC_DIR)/utils/*.java

all: compile

compile:
	@mkdir -p $(BIN_DIR)
	@$(JAVAC) -d $(BIN_DIR) -cp $(GSON_JAR) $(SOURCES)

server: compile
	@$(JAVA) -cp $(CLASSPATH) server.ServerMain

client: compile
	@$(JAVA) -cp $(CLASSPATH) client.ClientMain

clean:
	@rm -rf $(BIN_DIR)

reset: clean
	@rm -f $(DATA_DIR)/Users.json

.PHONY: all compile server client clean reset