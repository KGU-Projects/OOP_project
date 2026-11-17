package game.ui.screens;

import game.model.Stage;
import game.model.StageType;
import game.model.Tile;
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
            ########################################
            #....................................D.#
            #.........M..........................D.#
            #.........M.S........................D.#
            #.......#######..................#######
            #.................#######..............#
            #...........................A..........#
            #.#####..............................M.#
            #....................................MS#
            #....................................###
            #......######..........................#
            #..................................#...#
            #...S..................................#
            ######..........................#......#
            #............................#.........#
            #......................................#
            #.....#####...........#####............#
            #......................................#
            #.P....................................#
            ########################################
                        """;

    private final UserService userService;
    private Stage stage;

    public GameScreen(ScreenManager screenManager, UserService userService) {
        super(screenManager, new BorderLayout());
        this.userService = userService;

        this.stage = parseStage(mapPlaceHolder);

        panel.setBorder(null);
        panel.add(new MapCanvas(), BorderLayout.CENTER);
    }

    private static Stage parseStage(String rawMap) {
        String[] lines = rawMap.stripTrailing().split("\\R");
        int height = lines.length;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, line.length());
        }

        Tile[][] terrain = new Tile[height][width];
        for (Tile[] row : terrain) {
            Arrays.fill(row, Tile.EMPTY);
        }

        int startX = 1;
        int startY = 1;

        for (int y = 0; y < height; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                char c = line.charAt(x);
                if (c == Tile.SOLID.getSymbol()) {
                    terrain[y][x] = Tile.SOLID;
                } else {
                    terrain[y][x] = Tile.EMPTY;
                    if (c == 'P') {
                        startX = x;
                        startY = y;
                    }
                }
            }
        }

        return new Stage(1, "Test Stage", "", StageType.NORMAL, 1,
                terrain, startX, startY, List.of());
    }

    private class MapCanvas extends JPanel {
        MapCanvas() {
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
                        g2.drawRect(px, py, tileSize - 1, tileSize - 1);
                    }
                }
            }
        }
    }
}