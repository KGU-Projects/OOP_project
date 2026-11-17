package game.ui.screens;

import game.model.Stage;
import game.model.StageType;
import game.model.Tile;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.ScreenManager;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.List;

//메인 게임 화면.
public class GameScreen extends BaseScreen {

    private static final String mapPlaceHolder = """
            #######################################
            #...................................D.#
            #.........M.........................D.#
            #.........M.S.......................D.#
            #.......#######.................#######
            #.................######..............#
            #..........................A..........#
            #.#####.............................M.#
            #...................................MS#
            #...................................###
            #......######.........................#
            #.................................#...#
            #...S.................................#
            ######.........................#......#
            #...........................#.........#
            #.....................................#
            #.....####...........#####............#
            #.....................................#
            #.P...................................#
            #######################################
                        """;

    private final UserService userService;
    private final GameService gameService;
    private Stage stage;

    // 컨스트럭터
    public GameScreen(ScreenManager screenManager, UserService userService, GameService gameService) {
        super(screenManager, new BorderLayout());
        this.userService = userService;
        this.gameService = gameService;

        // 평문맵 해석하기
        this.stage = parseStage(mapPlaceHolder);

        panel.add(new blockPanel(), BorderLayout.CENTER);
    }

    private static Stage parseStage(String rawMap) {
        // 줄마다 찢기
        String[] lines = rawMap.stripTrailing().split("\\R");
        // 새로 높이 세로????
        int height = lines.length;
        int width = 0;
        // 가로 길이
        for (String line : lines) {
            width = Math.max(width, line.length());
        }

        // 맵좌표 오브젝트
        Tile[][] terrain = new Tile[height][width];
        for (Tile[] row : terrain) {
            Arrays.fill(row, Tile.EMPTY);
        }

        // 시작위치 세이브에서 받아올 것
        int pStartX = 1;
        int pStartY = 1;

        // 파싱한 평문맵에 따라 지형설정
        for (int y = 0; y < height; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                char c = line.charAt(x);
                if (c == Tile.SOLID.getSymbol()) {
                    terrain[y][x] = Tile.SOLID;
                } else {
                    terrain[y][x] = Tile.EMPTY;
                    if (c == 'P') {
                        pStartX = x;
                        pStartY = y;
                    }
                }
            }
        }

        return new Stage(1, "인천광역시", "어쩌고", StageType.NORMAL, 1,
                terrain, pStartX, pStartY, List.of());
    }

    // 화면등장에 쓸 패널
    private class blockPanel extends JPanel {
        blockPanel() {
            setBackground(new Color(0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (stage == null) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            int tileSize = Math.max(1, Math.min(
                    getWidth() / stage.getWidth(),
                    getHeight() / stage.getHeight()));
            int offsetX = (getWidth() - stage.getWidth() * tileSize) / 2;
            int offsetY = (getHeight() - stage.getHeight() * tileSize) / 2;

            for (int y = 0; y < stage.getHeight(); y++) {
                for (int x = 0; x < stage.getWidth(); x++) {
                    if (stage.isSolidTile(x, y)) {
                        int px = offsetX + x * tileSize;
                        int py = offsetY + y * tileSize;

                        g2.setColor(new Color(255, 255, 255));
                        g2.fillRect(px, py, tileSize, tileSize);
                    }
                }
            }
        }
    }
}