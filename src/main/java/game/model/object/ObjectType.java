package main.java.game.model.object;

/** 오브젝트 종류. symbol은 개발용 맵 미리보기에서 쓰는 글자이고 스테이지 파일에는 쓰지 않는다. */
public enum ObjectType {
    BUTTON('B'),
    DOOR('D'),
    KEY('K'),
    ITEM('I'),
    BOSS('M');

    private final char symbol;

    ObjectType(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }
}
