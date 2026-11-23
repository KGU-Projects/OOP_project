package game.model.object;

import game.model.GameState;

/** 세로 연속 3칸 출구(D). 들어가면(ENTER) 스테이지를 클리어한다. */
public class ExitObject extends GameObject {

    public ExitObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        super(id, x, y, quizId, rewardType, triggerType);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.EXIT;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public InteractionOutcome onEnter(GameState state) {
        if (state.isStageCleared()) {
            return InteractionOutcome.none();
        }
        state.setStageCleared(true);
        return InteractionOutcome.stageClear("출구 도달 - 스테이지 클리어!");
    }

    @Override
    public String describe() {
        return "출구 (높이 3)";
    }
}