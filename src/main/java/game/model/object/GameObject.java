package game.model.object;

import game.model.GameState;

import java.util.List;
import java.util.Optional;

/**
 * 맵 위의 오브젝트. 기존 오브젝트와 신규 스키마 오브젝트의 공통 부모 클래스.
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

    /** 기존 오브젝트(Boss, Button, Door, Item, Key) 호환 생성자 */
    protected GameObject(String id, int x, int y) {
        this(id, x, y, 0, RewardType.NONE, TriggerType.OPEN);
    }

    /** 신규 8칸 스키마 오브젝트 생성자 */
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

    public List<String> getLinkedIds() {
        return List.of();
    }

    public Optional<ObjectType> getLinkedType() {
        return Optional.empty();
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
        return !state.isObjectCollected(getId());
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