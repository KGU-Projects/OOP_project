package game.model;

import game.model.object.BossObject;
import game.model.object.GameObject;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 한 스테이지 플레이 중 변하는 런타임 상태.
 * 불변 객체인 Stage / GameObject 정의와 분리되어 플레이어 위치, 문 열림, 버튼 해결, 아이템 획득 등을 관리한다.
 */
public class GameState {

    private final Stage stage;
    private final Player player;
    private final int maxHp;
    private int hp;
    private int score;
    private final Map<ItemType, Integer> inventory;

    private final Set<String> openDoorIds = new HashSet<>();
    private final Set<String> solvedButtonIds = new HashSet<>();
    private final Set<String> collectedObjectIds = new HashSet<>();
    private final Set<String> resolvedObjectIds = new HashSet<>();

    private int bossMaxHp;
    private int bossHp;
    private boolean stageCleared;

    public GameState(Stage stage, int maxHp, SaveData saveData) {
        this.stage = stage;
        this.player = new Player(stage.getStartX(), stage.getStartY());
        this.maxHp = maxHp;
        this.inventory = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            this.inventory.put(type, 0);
        }

        if (saveData != null) {
            this.hp = Math.min(maxHp, Math.max(1, saveData.getHp()));
            this.score = saveData.getScore();
            this.inventory.putAll(saveData.getInventory());
        } else {
            this.hp = maxHp;
            this.score = 0;
        }

        for (GameObject obj : stage.getObjects()) {
            if (obj instanceof BossObject boss) {
                this.bossMaxHp = boss.getMaxHp();
                this.bossHp = boss.getMaxHp();
                break;
            }
        }
    }

    public Stage getStage() {
        return stage;
    }

    public Player getPlayer() {
        return player;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getHp() {
        return hp;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int delta) {
        this.score = Math.max(0, this.score + delta);
    }

    public void damage(int amount) {
        this.hp = Math.max(0, this.hp - amount);
    }

    public boolean heal(int amount) {
        if (hp >= maxHp) {
            return false;
        }
        this.hp = Math.min(maxHp, this.hp + amount);
        return true;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public int getItemCount(ItemType type) {
        return inventory.getOrDefault(type, 0);
    }

    public void addItem(ItemType type, int count) {
        if (count > 0) {
            inventory.merge(type, count, Integer::sum);
        }
    }

    /** 물약 사용 등 소모성 아이템 차감 */
    public boolean consumeItem(ItemType type) {
        if (type == ItemType.KEY) {
            return false;
        }
        int current = getItemCount(type);
        if (current <= 0) {
            return false;
        }
        inventory.put(type, current - 1);
        return true;
    }

    public Map<ItemType, Integer> getInventorySnapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(inventory));
    }

    public boolean isDoorOpen(String doorId) {
        return openDoorIds.contains(doorId);
    }

    public void openDoor(String doorId) {
        openDoorIds.add(doorId);
    }

    public boolean isButtonSolved(String buttonId) {
        return solvedButtonIds.contains(buttonId);
    }

    public void markButtonSolved(String buttonId) {
        solvedButtonIds.add(buttonId);
    }

    public boolean isObjectCollected(String objectId) {
        return collectedObjectIds.contains(objectId);
    }

    public void markObjectCollected(String objectId) {
        collectedObjectIds.add(objectId);
    }

    public boolean isObjectResolved(String objectId) {
        return resolvedObjectIds.contains(objectId);
    }

    public void markObjectResolved(String objectId) {
        resolvedObjectIds.add(objectId);
    }

    public boolean hasBoss() {
        return bossMaxHp > 0;
    }

    public int getBossMaxHp() {
        return bossMaxHp;
    }

    public int getBossHp() {
        return bossHp;
    }

    public void damageBoss(int amount) {
        this.bossHp = Math.max(0, this.bossHp - amount);
    }

    public boolean isStageCleared() {
        return stageCleared;
    }

    public void setStageCleared(boolean stageCleared) {
        this.stageCleared = stageCleared;
    }

    /** 스테이지 클리어 시점의 SaveData 생성 */
    public SaveData toSaveData(LocalDateTime savedAt) {
        return new SaveData(
                stage.getNumber(),
                Math.max(1, hp),
                score,
                inventory,
                savedAt);
    }
}