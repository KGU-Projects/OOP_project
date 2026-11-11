package io.github.OOP_project.model.object;

/** 보스 스테이지의 보스. 퀴즈를 맞히면 데미지를 입는다. */
public class BossObject extends GameObject {

    private final String name;
    private final int maxHp;

    public BossObject(String id, int x, int y, String name, int maxHp) {
        super(id, x, y);
        this.name = name;
        this.maxHp = maxHp;
    }

    public String getName() {
        return name;
    }

    public int getMaxHp() {
        return maxHp;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BOSS;
    }

    @Override
    public String describe() {
        return "보스  " + name + " 체력 " + maxHp;
    }
}
