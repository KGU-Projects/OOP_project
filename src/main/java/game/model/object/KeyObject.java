package main.java.game.model.object;

/** 얻으면 인벤토리에 열쇠(ItemType.KEY)가 하나 늘고 스테이지가 클리어되는 열쇠. */
public class KeyObject extends GameObject {

    public KeyObject(String id, int x, int y) {
        super(id, x, y);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.KEY;
    }

    @Override
    public String describe() {
        return "열쇠";
    }
}
