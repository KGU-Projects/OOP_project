package main.java.game.model;

/** 변하지 않는 지형 타일. 문·버튼 같은 오브젝트는 Tile이 아니라 GameObject로 다룬다. */
public enum Tile {
    EMPTY('.'),
    SOLID('#');

    private final char symbol;

    Tile(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }

    /** 파일의 지형 문자를 타일로 바꾼다. 'P'(시작 위치)는 호출하는 쪽에서 EMPTY로 처리한다. */
    public static Tile fromChar(char c) {
        for (Tile tile : values()) {
            if (tile.symbol == c) {
                return tile;
            }
        }
        throw new IllegalArgumentException("알 수 없는 지형 문자: " + c);
    }

    public boolean isSolid() {
        return this == SOLID;
    }
}
