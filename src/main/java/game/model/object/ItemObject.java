package game.model.object;

import game.model.GameState;
import game.model.ItemType;

/** 주우면 인벤토리에 들어가는 아이템. */
public class ItemObject extends GameObject {

    private static final int ITEM_SCORE = 50;

    private final ItemType itemType;
    private final int count;

    public ItemObject(String id, int x, int y, ItemType itemType, int count) {
        super(id, x, y);
        this.itemType = itemType;
        this.count = count;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public int getCount() {
        return count;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.ITEM;
    }

    @Override
    public TriggerType getTriggerType() {
        return TriggerType.STEP;
    }

    @Override
    public InteractionOutcome onStep(GameState state) {
        if (state.isObjectCollected(getId())) {
            return InteractionOutcome.none();
        }
        state.markObjectCollected(getId());
        state.markObjectResolved(getId());
        state.addItem(itemType, count);
        state.addScore(ITEM_SCORE * count);
        return InteractionOutcome.message(itemType.name() + " x" + count + " 획득");
    }

    @Override
    public String describe() {
        return "아이템  " + itemType.name() + " x" + count;
    }
}