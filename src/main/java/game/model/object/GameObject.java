package game.model.object;

import game.model.GameState;

import java.util.Optional;

/**
 * 맵 위의 오브젝트. 불변 정의와 다형적 상호작용·충돌 규칙을 가진다.
 */
public abstract class GameObject {

    public enum EffectType {
        NONE,
        MESSAGE,
        START_QUIZ,
        STAGE_CLEAR
    }

    public record InteractionOutcome(EffectType type, GameObject source, String message) {
        public static InteractionOutcome none() {
            return new InteractionOutcome(EffectType.NONE, null, "");
        }

        public static InteractionOutcome message(String text) {
            return new InteractionOutcome(EffectType.MESSAGE, null, text);
        }

        public static InteractionOutcome quiz(GameObject source) {
            return new InteractionOutcome(EffectType.START_QUIZ, source, "");
        }

        public static InteractionOutcome stageClear(String text) {
            return new InteractionOutcome(EffectType.STAGE_CLEAR, null, text);
        }
    }

    private final String id;
    private final int x;
    private final int y;
    private final int quizId;
    private final RewardType rewardType;
    private final TriggerType triggerType;

    protected GameObject(String id, int x, int y, int quizId, RewardType rewardType, TriggerType triggerType) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.quizId = quizId;
        this.rewardType = rewardType;
        this.triggerType = triggerType;
    }

    public abstract ObjectType getType();

    public abstract String describe();

    public String getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getEffectiveX(GameState state) {
        return x;
    }

    public int getEffectiveY(GameState state) {
        return y;
    }

    public int getQuizId() {
        return quizId;
    }

    public RewardType getRewardType() {
        return rewardType;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public int getWidth() {
        return 1;
    }

    public int getHeight() {
        return 1;
    }

    public boolean occupiesTile(int tx, int ty, GameState state) {
        int ex = getEffectiveX(state);
        int ey = getEffectiveY(state);
        return tx >= ex && tx < ex + getWidth()
                && ty >= ey && ty < ey + getHeight();
    }

    public boolean isSolid(GameState state) {
        return false;
    }

    public boolean isVisible(GameState state) {
        return true;
    }

    public double getProximityRange() {
        return 0.0;
    }

    public InteractionOutcome onProximity(GameState state) {
        return InteractionOutcome.none();
    }

    public InteractionOutcome onStep(GameState state) {
        return InteractionOutcome.none();
    }

    public InteractionOutcome onEnter(GameState state) {
        return InteractionOutcome.none();
    }

    public boolean canInteract(GameState state) {
        return false;
    }

    public double getInteractionRange() {
        return 0.35;
    }

    public String getInteractPrompt() {
        return "";
    }

    public InteractionOutcome onInteract(GameState state) {
        return InteractionOutcome.none();
    }

    /** 퀴즈를 맞혔을 때 오브젝트별 후처리를 수행하고 안내 메시지를 반환한다. */
    public String onQuizSolved(GameState state) {
        state.markObjectResolved(getId());
        return "정답입니다!";
    }
}