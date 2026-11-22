package game.model.object;

import game.model.GameState;

/** 화면에 보이지 않는 함정(.). 밟으면(STEP) 퀴즈가 발동한다. */
public class TrapObject extends GameObject {

    public TrapObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        super(id, x, y, quizId, rewardType, triggerType);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.TRAP;
    }

    @Override
    public boolean isVisible(GameState state) {
        return state.isObjectResolved(getId());
    }

    @Override
    public InteractionOutcome onStep(GameState state) {
        if (state.isObjectResolved(getId())) {
            return InteractionOutcome.none();
        }
        return InteractionOutcome.quiz(this);
    }

    @Override
    public String onQuizSolved(GameState state) {
        state.markObjectResolved(getId());
        return "함정 해제!";
    }

    @Override
    public String describe() {
        return "함정  문제 " + getQuizId();
    }
}