package game.model.object;

import game.model.GameState;

/** 보스 스테이지의 보스. 퀴즈를 맞히면 데미지를 입는다. */
public class BossObject extends GameObject {

    private static final int BOSS_HIT_SCORE = 150;

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
    public int getQuizId() {
        return super.getQuizId() > 0 ? super.getQuizId() : 1;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BOSS;
    }

    @Override
    public boolean isVisible(GameState state) {
        return state.getBossHp() > 0;
    }

    @Override
    public boolean canInteract(GameState state) {
        return state.getBossHp() > 0;
    }

    @Override
    public double getInteractionRange() {
        return 0.45;
    }

    @Override
    public String getInteractPrompt() {
        return "[E] " + name;
    }

    @Override
    public InteractionOutcome onInteract(GameState state) {
        if (!canInteract(state)) {
            return InteractionOutcome.none();
        }
        return InteractionOutcome.quiz(this);
    }

    @Override
    public String onQuizSolved(GameState state) {
        state.damageBoss(1);
        state.addScore(BOSS_HIT_SCORE);
        if (state.getBossHp() <= 0) {
            state.markObjectResolved(getId());
            state.setStageCleared(true);
            return name + " 격파!";
        }
        return name + "에게 데미지! (남은 체력: " + state.getBossHp() + ")";
    }

    @Override
    public String describe() {
        return "보스  " + name + " 체력 " + maxHp;
    }
}