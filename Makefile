# CODE ESCAPE 빌드 도우미. 프로젝트 루트에서 make 를 실행한다.
#
#   make                  컴파일 (all)
#   make run              게임 실행 (GameApp.main)
#   make preview          스테이지 미리보기: 모든 스테이지
#   make preview STAGE=3  스테이지 미리보기: 3스테이지만
#   make clean            컴파일된 .class 삭제
#   make fclean           out/ 폴더까지 삭제
#   make re               fclean 후 다시 컴파일

JAVAC   ?= javac
JAVA    ?= java
SRC_DIR := src
OUT_DIR := out

JAVAC_FLAGS := -encoding UTF-8 -Xlint:all
MAIN := game.GameApp
PREVIEW_MAIN := game.tool.StagePreviewMain

# 번호를 주면 그 스테이지만, 비우면 모든 스테이지를 미리본다
STAGE ?=

SOURCES := $(shell find $(SRC_DIR) -name '*.java')

# 컴파일이 끝났다는 표시. 소스가 이 파일보다 새로우면 다시 컴파일한다.
STAMP := $(OUT_DIR)/.compiled

.PHONY: all clean fclean run preview re

all: $(STAMP)

$(STAMP): $(SOURCES)
	@mkdir -p $(OUT_DIR)
	@$(JAVAC) $(JAVAC_FLAGS) -d $(OUT_DIR) $(SOURCES)
	@touch $(STAMP)

# 게임 실행. 컴파일이 먼저 되어 있어야 한다
run: all
	$(JAVA) -Dfile.encoding=UTF-8 -cp $(OUT_DIR):src/main/resources $(MAIN)

# 미리보기는 컴파일이 먼저 되어 있어야 한다
preview: all
	$(JAVA) -cp $(OUT_DIR) $(PREVIEW_MAIN) $(STAGE)

# .class 와 컴파일 표시만 지운다 (out/ 폴더는 남긴다)
clean:
	@if [ -d $(OUT_DIR) ]; then find $(OUT_DIR) -name '*.class' -delete; fi
	@rm -f $(STAMP)

# out/ 폴더까지 모두 지운다
fclean: clean
	@rm -rf $(OUT_DIR)

re: fclean all
