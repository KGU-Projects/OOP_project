package io.github.OOP_project.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 저장된 진행 상황 하나(슬롯 하나). 값이 바뀌지 않는 객체이고, 파일 한 줄과 필드 순서가 같다.
 *
 * 저장하는 것은 "마지막으로 클리어한 스테이지"까지다. 스테이지 안의 위치, 열린 문, 푼 버튼, 주운 아이템,
 * 보스 체력은 저장하지 않으므로 이어하기는 다음 스테이지의 처음부터 시작한다.
 * 열쇠는 아이템(ItemType.KEY)이라 인벤토리에 함께 저장된다.
 * 최대 체력은 저장하지 않고 상수에서 가져온다. 슬롯 번호는 파일 이름이 정하므로 여기에 두지 않는다.
 */
public class SaveData {

    /** 아직 클리어한 스테이지가 없음을 뜻하는 값. 이어하기는 1스테이지부터 시작한다. */
    public static final int NONE_CLEARED = 0;

    private final int clearedStage;               // 마지막으로 클리어한 스테이지 번호
    private final int hp;                         // 그 스테이지를 클리어했을 때의 체력
    private final int score;
    private final Map<ItemType, Integer> inventory;
    private final LocalDateTime savedAt;

    public SaveData(int clearedStage, int hp, int score, Map<ItemType, Integer> inventory,
                    LocalDateTime savedAt) {
        if (clearedStage < NONE_CLEARED) {
            throw new IllegalArgumentException("마지막 클리어 스테이지는 0 이상이어야 합니다: " + clearedStage);
        }
        if (hp < 1) {
            throw new IllegalArgumentException("저장된 체력은 1 이상이어야 합니다: " + hp);
        }
        if (score < 0) {
            throw new IllegalArgumentException("저장된 점수는 0 이상이어야 합니다: " + score);
        }
        this.clearedStage = clearedStage;
        this.hp = hp;
        this.score = score;
        this.inventory = immutableCopy(inventory);
        this.savedAt = savedAt;
    }

    // 넘겨받은 맵을 복사해 두어, 나중에 바깥에서 바꿔도 저장 상태가 변하지 않게 한다.
    private static Map<ItemType, Integer> immutableCopy(Map<ItemType, Integer> source) {
        Map<ItemType, Integer> copy = new EnumMap<>(ItemType.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    /** 이어하기로 시작할 스테이지 번호. */
    public int getNextStageNo() {
        return clearedStage + 1;
    }

    public int getClearedStage() {
        return clearedStage;
    }

    public int getHp() {
        return hp;
    }

    public int getScore() {
        return score;
    }

    public Map<ItemType, Integer> getInventory() {
        return inventory;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}
