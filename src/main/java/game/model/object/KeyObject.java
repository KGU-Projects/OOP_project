package game.model.object;

import game.model.GameState;
import game.model.ItemType;

/** 얻으면 인벤토리에 열쇠(ItemType.KEY)가 하나 늘고 스테이지가 클리어되는 열쇠. */
public class KeyObject extends GameObject {

    private static final int KEY_CLEAR_SCORE = 200;

    public KeyObject(String id, int x, int y) {
        super(id, x, y);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.KEY;
    }

    @Override
    public TriggerType getTriggerType() {
        return TriggerType.ENTER;
    }

    @Override
    public InteractionOutcome onEnter(GameState state) {
        if (state.isObjectCollected(getId())) {
            return InteractionOutcome.none();
        }
        state.markObjectCollected(getId());
        state.markObjectResolved(getId());
        state.addItem(ItemType.KEY, 1);
        state.addScore(KEY_CLEAR_SCORE);
        state.setStageCleared(true);
        return InteractionOutcome.stageClear("열쇠 획득 - 스테이지 클리어!");
    }

    @Override
    public String describe() {
        return "열쇠";
    }
}