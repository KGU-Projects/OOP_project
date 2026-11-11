package game.model.object;

import java.util.List;
import java.util.Optional;

/** 누르면 퀴즈가 나오고, 맞히면 연결된 문이 열리는 버튼. */
public class ButtonObject extends GameObject {

    private final int quizId;
    private final List<String> doorIds;

    public ButtonObject(String id, int x, int y, int quizId, List<String> doorIds) {
        super(id, x, y);
        this.quizId = quizId;
        this.doorIds = List.copyOf(doorIds);
    }

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
    public String describe() {
        return "버튼  문제 " + quizId + " → " + String.join(",", doorIds);
    }
}
