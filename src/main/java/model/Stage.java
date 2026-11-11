package main.java.model;

import main.java.common.GameConstants;
import main.java.model.object.GameObject;

import java.util.List;
import java.util.Optional;

/** 스테이지 하나: 정보, 지형 격자, 오브젝트 목록. 좌표는 왼쪽 위가 (0, 0)인 타일 단위다. */
public class Stage {

    private final int number;
    private final String title;
    private final String concept;
    private final StageType type;
    private final int theme;
    private final int width;
    private final int height;
    private final Tile[][] terrain;      // terrain[y][x]
    private final int startTileX;
    private final int startTileY;
    private final List<GameObject> objects;

    public Stage(int number, String title, String concept, StageType type, int theme,
                 Tile[][] terrain, int startTileX, int startTileY, List<GameObject> objects) {
        this.number = number;
        this.title = title;
        this.concept = concept;
        this.type = type;
        this.theme = theme;
        this.height = terrain.length;
        this.width = terrain[0].length;
        this.terrain = copyOf(terrain);
        this.startTileX = startTileX;
        this.startTileY = startTileY;
        this.objects = List.copyOf(objects);
    }

    private static Tile[][] copyOf(Tile[][] source) {
        Tile[][] copy = new Tile[source.length][];
        for (int y = 0; y < source.length; y++) {
            copy[y] = source[y].clone();
        }
        return copy;
    }

    /** 맵 밖은 SOLID로 취급한다. 플레이어가 가장자리를 벗어나지 못하게 하기 위해서다. */
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

    /** 'P' 타일 위에 발이 닿게 서는 x 좌표: 타일 가운데에 몸체 폭을 맞춘다. */
    public double getStartX() {
        return startTileX + (1 - GameConstants.PLAYER_WIDTH) / 2;
    }

    /** 'P' 타일 위에 발이 닿게 서는 y 좌표: 타일 바닥에서 키만큼 올라간 위치. */
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

    public StageType getType() {
        return type;
    }

    public int getTheme() {
        return theme;
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
