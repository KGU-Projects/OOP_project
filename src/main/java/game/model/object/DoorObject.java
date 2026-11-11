package game.model.object;

/** 폭 1타일, 아래로 height만큼 막는 문. 처음에는 닫혀 있다. */
public class DoorObject extends GameObject {

    private final int height;

    public DoorObject(String id, int x, int y, int height) {
        super(id, x, y);
        this.height = height;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.DOOR;
    }

    @Override
    public String describe() {
        return "문  높이 " + height;
    }
}
