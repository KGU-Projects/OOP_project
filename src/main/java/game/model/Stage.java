package game.model;

import game.common.GameConstants;
import game.model.object.GameObject;

import java.util.List;
import java.util.Optional;

/** 스테이지 하나: 정보(번호|제목|개념|가로|세로), 지형 격자, 오브젝트 목록. */
public class Stage {

    private final int number;
    private final String title;
    private final String concept;
    private final int width;
    private final int height;
    private final Tile[][] terrain;
    private final int startTileX;
    private final int startTileY;
    private final List<GameObject> objects;

    public Stage(int number, String title, String concept,
            Tile[][] terrain, int startTileX, int startTileY, List<GameObject> objects) {
        this.number = number;
        this.title = title;
        this.concept = concept;
        this.height = terrain.length;
        this.width = terrain[0].length;
        this.terrain = copyOf(terrain);
        this.startTileX = startTileX;
        this.startTileY = startTileY;
        this.objects = List.copyOf(objects);
    }

    /** 기존 호출부 호환용 생성자 */
    public Stage(int number, String title, String concept, StageType ignoredType, int ignoredTheme,
            Tile[][] terrain, int startTileX, int startTileY, List<GameObject> objects) {
        this(number, title, concept, terrain, startTileX, startTileY, objects);
    }

    private static Tile[][] copyOf(Tile[][] source) {
        Tile[][] copy = new Tile[source.length][];
        for (int y = 0; y < source.length; y++) {
            copy[y] = source[y].clone();
        }
        return copy;
    }

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return Tile.SOLID;
        }
        return terrain[y][x];
    }

    public boolean isSolidTile(int x, int y) {
        return getTile(x, y).isSolid();
    }

    public Optional<GameObject> findObject(String id) {
        return objects.stream().filter(o -> o.getId().equals(id)).findFirst();
    }

    public double getStartX() {
        return startTileX + (1 - GameConstants.PLAYER_WIDTH) / 2;
    }

    public double getStartY() {
        return startTileY + 1 - GameConstants.PLAYER_HEIGHT;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getConcept() {
        return concept;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getStartTileX() {
        return startTileX;
    }

    public int getStartTileY() {
        return startTileY;
    }

    public List<GameObject> getObjects() {
        return objects;
    }
}