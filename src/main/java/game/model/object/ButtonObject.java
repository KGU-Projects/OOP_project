package game.model.object;

import game.model.GameState;

import java.util.List;
import java.util.Optional;

/** 누르면 퀴즈가 나오고, 맞히면 연결된 문이 열리는 버튼. */
public class ButtonObject extends GameObject {

    private static final int BUTTON_QUIZ_SCORE = 100;

    private final int quizId;
    private final List<String> doorIds;

    public ButtonObject(String id, int x, int y, int quizId, List<String> doorIds) {
        super(id, x, y);
        this.quizId = quizId;
        this.doorIds = List.copyOf(doorIds);
    }

    @Override
    public int getQuizId() {
        return quizId;
    }

    public List<String> getDoorIds() {
        return doorIds;
    }

    @Override
    public List<String> getLinkedIds() {
        return doorIds;
    }

    @Override
    public Optional<ObjectType> getLinkedType() {
        return Optional.of(ObjectType.DOOR);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BUTTON;
    }

    @Override
    public boolean canInteract(GameState state) {
        return !state.isButtonSolved(getId());
    }

    @Override
    public double getInteractionRange() {
        return 0.25;
    }

    @Override
    public String getInteractPrompt() {
        return "[E] 퀴즈";
    }

    @Override
    public InteractionOutcome onInteract(GameState state) {
        if (!canInteract(state)) {
            return InteractionOutcome.none();
        }
        return InteractionOutcome.quiz(this);
    }

    @Override
    public String onQuizSolved(GameState state) {
        state.markButtonSolved(getId());
        state.markObjectResolved(getId());
        for (String doorId : doorIds) {
            state.openDoor(doorId);
        }
        state.addScore(BUTTON_QUIZ_SCORE);
        return "정답! 문이 열렸습니다";
    }

    @Override
    public String describe() {
        return "버튼  문제 " + quizId + " → " + String.join(",", doorIds);
    }
}