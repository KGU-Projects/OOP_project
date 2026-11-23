package game.model.object;

import game.model.GameState;

/** 접근(NEAR)하면 지정 좌표로 한 번만 이동한 뒤 고정되는 발판(A). */
public class MovePlatformObject extends GameObject {

    private final int targetX;
    private final int targetY;

    public MovePlatformObject(String id, int x, int y, int quizId, RewardType rewardType,
                              TriggerType triggerType, int targetX, int targetY) {
        super(id, x, y, quizId, rewardType, triggerType);
        this.targetX = targetX;
        this.targetY = targetY;
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.MOVE_PLATFORM;
    }

    @Override
    public int getEffectiveX(GameState state) {
        return state.isObjectResolved(getId()) ? targetX : getX();
    }

    @Override
    public int getEffectiveY(GameState state) {
        return state.isObjectResolved(getId()) ? targetY : getY();
    }

    @Override
    public boolean isSolid(GameState state) {
        return true;
    }

    @Override
    public double getProximityRange() {
        return 1.2;
    }

    @Override
    public InteractionOutcome onProximity(GameState state) {
        if (state.isObjectResolved(getId())) {
            return InteractionOutcome.none();
        }
        state.markObjectResolved(getId());
        return InteractionOutcome.message("이동 발판 작동");
    }

    @Override
    public String describe() {
        return "이동 발판 → (" + targetX + "," + targetY + ")";
    }
}