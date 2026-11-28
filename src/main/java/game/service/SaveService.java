package game.service;

import game.common.GameConstants;
import game.model.GameState;
import game.model.ItemType;
import game.model.SaveData;
import game.repository.SaveRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 저장 로직 */
public class SaveService {
    // 저장 창고
    private final SaveRepository saveRepository;
    // 지금 쓰고 있는 저장 칸 번호 (0이면 저장 안 함)
    private int currentSlot;

    /** 생성자 */
    public SaveService(SaveRepository saveRepository) {
        this.saveRepository = saveRepository;
    }

    /** 현재 저장 칸 번호 */
    public int getCurrentSlot() {
        return currentSlot;
    }

    /** 새 게임이거나 사용자가 바뀌면 부름. 0으로 초기화 */
    public void resetCurrentSlot() {
        currentSlot = 0;
    }

    /** 저장 칸 하나 읽고 GameService에 리턴. 비어 있으면 오류 */
    public SaveData load(String nickname, int slot) {
        SaveData save = saveRepository.find(nickname, slot)
                .orElseThrow(() -> new IllegalStateException(slot + "번 저장 칸이 비어 있습니다"));
        currentSlot = slot;
        return save;
    }

    /** 저장 칸 삭제 */
    public void delete(String nickname, int slot) {
        saveRepository.delete(nickname, slot);
        // 지금 쓰던 칸을 지웠으면 칸 번호도 비우기
        if (currentSlot == slot) {
            resetCurrentSlot();
        }
    }

    /** 저장 칸 1~3 목록 (불러오기 화면용) */
    public List<Optional<SaveData>> getAllSlots(String nickname) {
        List<Optional<SaveData>> slots = new ArrayList<>(GameConstants.SAVE_SLOT_COUNT);
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            slots.add(nickname == null ? Optional.empty() : saveRepository.find(nickname, slot));
        }
        return slots;
    }

    /** 저장할 칸 고르기 */
    private int resolveAutoSaveSlot(String nickname) {
        if (currentSlot >= 1 && currentSlot <= GameConstants.SAVE_SLOT_COUNT) return currentSlot;
        int oldestSlot = 1;
        LocalDateTime oldestTime = null;
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            Optional<SaveData> existing = saveRepository.find(nickname, slot);
            if (existing.isEmpty()) return slot;
            LocalDateTime savedAt = existing.get().getSavedAt();
            if (oldestTime == null || savedAt.isBefore(oldestTime)) {
                oldestTime = savedAt;
                oldestSlot = slot;
            }
        }
        return oldestSlot;
    }

    /** 클리어하면 자동 저장. 저장한 칸 번호 돌려줌 */
    public int autoSave(String nickname, GameState state) {
        int targetSlot = resolveAutoSaveSlot(nickname);
        // 1개 이상인 아이템만 저장
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            int count = state.getItemCount(type);
            if (count > 0) inventory.put(type, count);
        }
        SaveData data = new SaveData(state.getStage().getNumber(), state.getHp(), state.getScore(), inventory,
                LocalDateTime.now());
        saveRepository.save(nickname, targetSlot, data);
        // 이 칸을 쓰고 있다고 기억
        currentSlot = targetSlot;
        return targetSlot;
    }
}