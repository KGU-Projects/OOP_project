package game.model.object;

import game.model.GameState;

/** 일반 상자(S)처럼 보이지만 열면(OPEN) 퀴즈가 나오고 보상이 없는 미믹. */
public class MimicObject extends GameObject {

    public MimicObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        super(id, x, y, quizId, rewardType, triggerType);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.MIMIC;
    }

    @Override
    public boolean canInteract(GameState state) {
        return !state.isObjectResolved(getId());
    }

    @Override
    public String getInteractPrompt() {
        return "[E] 상자 열기";
    }

    @Override
    public InteractionOutcome onInteract(GameState state) {
        if (state.isObjectResolved(getId())) {
            return InteractionOutcome.none();
        }
        return InteractionOutcome.quiz(this);
    }

    @Override
    public String onQuizSolved(GameState state) {
        state.markObjectResolved(getId());
        return "미믹 함정 돌파! (보상 없음)";
    }

    @Override
    public String describe() {
        return "미믹  문제 " + getQuizId();
    }
}