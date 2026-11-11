package io.github.OOP_project.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 저장된 게임 상태 하나(슬롯 하나). 값이 바뀌지 않는 객체이고, 파일 한 줄과 필드 순서가 같다.
 * 최대 체력은 저장하지 않고 상수에서 가져온다. 슬롯 번호는 파일 이름이 정하므로 여기에 두지 않는다.
 */
public class SaveData {

    /** bossHp가 의미 없는 스테이지(보스 스테이지가 아닐 때)에 쓰는 값. */
    public static final int NO_BOSS = -1;

    private final int stageNo;
    private final double x;
    private final double y;
    private final int hp;
    private final int score;
    private final Map<ItemType, Integer> inventory;
    private final Set<Integer> keys;                  // 획득한 열쇠가 속한 스테이지 번호
    private final Set<String> openedDoorIds;          // 현재 스테이지 안의 오브젝트 ID
    private final Set<String> solvedObjectIds;
    private final Set<String> collectedObjectIds;
    private final int bossHp;
    private final int stageEntryScore;                // 게임 오버 후 "처음부터 시작"에서 되돌릴 값
    private final Map<ItemType, Integer> stageEntryInventory;
    private final LocalDateTime savedAt;

    public SaveData(int stageNo, double x, double y, int hp, int score,
                    Map<ItemType, Integer> inventory, Set<Integer> keys,
                    Set<String> openedDoorIds, Set<String> solvedObjectIds, Set<String> collectedObjectIds,
                    int bossHp, int stageEntryScore, Map<ItemType, Integer> stageEntryInventory,
                    LocalDateTime savedAt) {
        if (stageNo < 1) {
            throw new IllegalArgumentException("스테이지 번호는 1 이상이어야 합니다: " + stageNo);
        }
        if (hp < 1) {
            throw new IllegalArgumentException("저장된 체력은 1 이상이어야 합니다: " + hp);
        }
        this.stageNo = stageNo;
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.score = score;
        this.inventory = immutableCopy(inventory);
        this.keys = immutableCopy(keys);
        this.openedDoorIds = immutableCopy(openedDoorIds);
        this.solvedObjectIds = immutableCopy(solvedObjectIds);
        this.collectedObjectIds = immutableCopy(collectedObjectIds);
        this.bossHp = bossHp;
        this.stageEntryScore = stageEntryScore;
        this.stageEntryInventory = immutableCopy(stageEntryInventory);
        this.savedAt = savedAt;
    }

    // 넘겨받은 컬렉션을 복사해 두어, 나중에 바깥에서 바꿔도 저장 상태가 변하지 않게 한다.
    private static <T> Set<T> immutableCopy(Set<T> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    private static Map<ItemType, Integer> immutableCopy(Map<ItemType, Integer> source) {
        Map<ItemType, Integer> copy = new EnumMap<>(ItemType.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    public int getStageNo() {
        return stageNo;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
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

    public Set<Integer> getKeys() {
        return keys;
    }

    public Set<String> getOpenedDoorIds() {
        return openedDoorIds;
    }

    public Set<String> getSolvedObjectIds() {
        return solvedObjectIds;
    }

    public Set<String> getCollectedObjectIds() {
        return collectedObjectIds;
    }

    public int getBossHp() {
        return bossHp;
    }

    public int getStageEntryScore() {
        return stageEntryScore;
    }

    public Map<ItemType, Integer> getStageEntryInventory() {
        return stageEntryInventory;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}
