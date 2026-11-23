package game.model.object;

import game.model.GameState;

/** 세로 연속 2칸 몬스터. 접근(NEAR)하면 퀴즈가 시작된다. */
public class MonsterObject extends GameObject {

    private static final int DEFEAT_SCORE = 150;

    public MonsterObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        super(id, x, y, quizId, rewardType, triggerType);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.MONSTER;
    }

    @Override
    public int getHeight() {
        return 2;
    }

    @Override
    public boolean isVisible(GameState state) {
        return !state.isObjectResolved(getId());
    }

    @Override
    public double getProximityRange() {
        return 0.6;
    }

    @Override
    public InteractionOutcome onProximity(GameState state) {
        if (state.isObjectResolved(getId())) {
            return InteractionOutcome.none();
        }
        return InteractionOutcome.quiz(this);
    }

    @Override
    public String onQuizSolved(GameState state) {
        state.markObjectResolved(getId());
        state.addScore(DEFEAT_SCORE);
        return "몬스터 처치! (+" + DEFEAT_SCORE + "점)";
    }

    @Override
    public String describe() {
        return "몬스터  문제 " + getQuizId();
    }
}