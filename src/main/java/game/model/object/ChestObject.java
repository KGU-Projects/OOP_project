package game.model.object;

import game.model.GameState;
import game.model.ItemType;

/** 일반 상자(S). 열면(OPEN) 아이템을 획득한다. */
public class ChestObject extends GameObject {

    private static final int CHEST_SCORE = 50;

    public ChestObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        super(id, x, y, quizId, rewardType, triggerType);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.CHEST;
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
        state.markObjectResolved(getId());
        if (getRewardType() == RewardType.AUTO) {
            state.addItem(ItemType.HEALTH_POTION, 1);
            state.addScore(CHEST_SCORE);
            return InteractionOutcome.message("상자에서 체력 물약 획득!");
        }
        return InteractionOutcome.message("빈 상자입니다.");
    }

    @Override
    public String describe() {
        return "일반 상자 (" + getRewardType() + ")";
    }
}