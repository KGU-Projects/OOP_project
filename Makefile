# CODE ESCAPE build helper. Run make from the project root.
#
#   make                  compile
#   make run              run the game
#   make preview          preview all stages
#   make preview STAGE=3  preview stage 3
#   make clean            delete compiled .class files
#   make fclean           delete out/
#   make re               clean and compile again
#
# Maven is used to fetch the MP3 decoder dependency declared in pom.xml.

JAVAC   ?= javac
JAVA    ?= java
MAVEN   ?= mvn
SRC_DIR := src
OUT_DIR := out
LIB_DIR := $(OUT_DIR)/lib

JAVAC_FLAGS := -encoding UTF-8 -Xlint:all
MAIN := game.GameApp
PREVIEW_MAIN := game.tool.StagePreviewMain

STAGE ?=
SOURCES := $(shell find $(SRC_DIR) -name '*.java')
STAMP := $(OUT_DIR)/.compiled

.PHONY: all clean fclean run preview re

all: $(STAMP)

$(STAMP): $(SOURCES) pom.xml
	@mkdir -p $(OUT_DIR) $(LIB_DIR)
	@$(MAVEN) -q dependency:copy-dependencies -DoutputDirectory=$(abspath $(LIB_DIR))
	@$(JAVAC) $(JAVAC_FLAGS) -cp "$(LIB_DIR)/*" -d $(OUT_DIR) $(SOURCES)
	@touch $(STAMP)

run: all
	$(JAVA) -Dfile.encoding=UTF-8 -cp "$(OUT_DIR):src/main/resources:$(LIB_DIR)/*" $(MAIN)

preview: all
	$(JAVA) -cp "$(OUT_DIR):$(LIB_DIR)/*" $(PREVIEW_MAIN) $(STAGE)

clean:
	@if [ -d $(OUT_DIR) ]; then find $(OUT_DIR) -name '*.class' -delete; fi
	@rm -f $(STAMP)

fclean: clean
	@rm -rf $(OUT_DIR)

re: fclean all
