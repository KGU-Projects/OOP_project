package game.model.object;

import java.util.List;
import java.util.Optional;

/**
 * 맵 위의 오브젝트. 변하지 않는 정의만 가진다.
 * 열린 문, 푼 버튼 같은 변하는 상태는 GameState가 보관한다.
 * isSolid(GameState), interact(GameState), onQuizSolved(...)는 GameState를 만들 때 추가한다.
 */
public abstract class GameObject {

    private final String id;
    private final int x;
    private final int y;

    protected GameObject(String id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public abstract ObjectType getType();

    /** 미리보기 범례에 쓰는 사람이 읽는 설명. 예: "버튼  문제 7 → d1" */
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

    /** 이 오브젝트가 가리키는 다른 오브젝트의 ID들. 가리키는 것이 없으면 빈 목록. 예: 버튼이 여는 문. */
    public List<String> getLinkedIds() {
        return List.of();
    }

    /** getLinkedIds()가 가리키는 오브젝트가 가져야 하는 종류. 데이터 검증에 쓴다. */
    public Optional<ObjectType> getLinkedType() {
        return Optional.empty();
    }

    /** 차지하는 타일 폭. 기본은 1칸. */
    public int getWidth() {
        return 1;
    }

    /** 차지하는 타일 높이. 기본은 1칸. */
    public int getHeight() {
        return 1;
    }
}
