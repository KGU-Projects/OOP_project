package game.tool;

import game.model.Stage;
import game.model.Tile;
import game.model.object.GameObject;

/**
 * 스테이지의 지형과 오브젝트를 겹쳐 글자 그림으로 만든다. 개발용이며 파일을 읽지 않는다.
 * 오브젝트 글자는 ObjectType이 가지고 있어서 종류별 분기를 하지 않는다.
 */
public class StagePreview {

    private static final char START_SYMBOL = 'P';
    private static final char OVERLAP_SYMBOL = '*';
    private static final char EMPTY_SYMBOL = Tile.EMPTY.getSymbol();

    public String render(Stage stage) {
        char[][] grid = buildGrid(stage);
        StringBuilder out = new StringBuilder();

        out.append(String.format("스테이지 %d \"%s\" (%s, %s) %d x %d%n%n",
                stage.getNumber(), stage.getTitle(), stage.getConcept(), stage.getType(),
                stage.getWidth(), stage.getHeight()));

        // x 눈금: 각 칸의 x 일의 자리
        out.append("      x: ");
        for (int x = 0; x < stage.getWidth(); x++) {
            out.append(x % 10);
        }
        out.append(System.lineSeparator());

        for (int y = 0; y < stage.getHeight(); y++) {
            out.append(String.format(" %-6s  ", "y=" + y)).append(new String(grid[y])).append(System.lineSeparator());
        }

        out.append(System.lineSeparator());
        for (GameObject object : stage.getObjects()) {
            String position = "(" + object.getX() + ", " + object.getY() + ")";
            out.append(String.format(" %c  %-6s %-10s %s%n",
                    object.getType().getSymbol(), object.getId(), position, object.describe()));
        }
        out.append(String.format("%n # 단단한 블록, . 빈 공간, P 시작 위치, %c 겹친 칸(확인 필요)%n", OVERLAP_SYMBOL));
        return out.toString();
    }

    /**
     * 지형 위에 시작 위치와 오브젝트를 올린다.
     * 이미 무언가 놓인 칸(블록, 시작 위치, 다른 오브젝트)에 또 올리면 '*'로 표시한다.
     */
    private char[][] buildGrid(Stage stage) {
        char[][] grid = new char[stage.getHeight()][stage.getWidth()];
        for (int y = 0; y < stage.getHeight(); y++) {
            for (int x = 0; x < stage.getWidth(); x++) {
                grid[y][x] = stage.getTile(x, y).getSymbol();
            }
        }
        place(grid, stage.getStartTileX(), stage.getStartTileY(), START_SYMBOL);
        for (GameObject object : stage.getObjects()) {
            for (int dy = 0; dy < object.getHeight(); dy++) {
                for (int dx = 0; dx < object.getWidth(); dx++) {
                    place(grid, object.getX() + dx, object.getY() + dy, object.getType().getSymbol());
                }
            }
        }
        return grid;
    }

    private void place(char[][] grid, int x, int y, char symbol) {
        if (y < 0 || y >= grid.length || x < 0 || x >= grid[y].length) {
            return;   // 범위 밖은 그리지 않는다 (파일에서 읽은 스테이지는 이미 검증됨)
        }
        grid[y][x] = (grid[y][x] == EMPTY_SYMBOL) ? symbol : OVERLAP_SYMBOL;
    }
}
