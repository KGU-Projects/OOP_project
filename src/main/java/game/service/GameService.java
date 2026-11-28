package game.service;

import game.audio.AudioManager;
import game.audio.AudioManager.Sound;
import game.common.GameConstants;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.Player;
import game.model.SaveData;
import game.model.Stage;
import game.model.User;
import game.model.object.GameObject;
import game.model.object.ObjectType;
import game.model.quiz.Quiz;
import game.repository.QuizRepository;
import game.repository.SaveRepository;
import game.repository.StageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class GameService {
    private static final int FIRST_STAGE = 1;
    private static final int MAX_HP = 3;
    private static final int WRONG_ANSWER_DAMAGE = 1;
    private static final int ENEMY_DAMAGE = 1;
    // 파일에 적 HP가 0이면 이걸로
    private static final int DEFAULT_ENEMY_HP = 3;
    // 미니게임 성공 시 회복량
    private static final int MINIGAME_HEAL = 2;
    // 일반 상자랑 같은 점수
    private static final int KEY_CHEST_SCORE = 50;

    private final QuizRepository quizRepository;
    private final StageRepository stageRepository;
    private final SaveRepository saveRepository;
    private final UserService userService;
    private User user;
    private String nickname;
    private GameState state;
    private int currentSlot;
    private Quiz currentQuiz;
    private GameObject currentTarget;

    // 스테이지 들어온 순간의 점수와 아이템 기억(죽고 현 스테이지 다시 시작 시 필요)
    // null이면 새 게임이라 점수 0, 아이템 없음
    private SaveData stageEntry;
    // 적 ID -> 남은 HP (전투 중단해도 안 지움)
    private final Map<String, Integer> enemyHp = new HashMap<>();
    // 열쇠 들어있는 상자 ID
    private String keyChestId;
    // 틀려서 HP 1 됐는데 물약 있음 -> 쓸지 물어보는 중
    private boolean askingPotion;
    private final Random random = new Random();

    public GameService(QuizRepository quizRepository, StageRepository stageRepository, SaveRepository saveRepository) {
        this(quizRepository, stageRepository, saveRepository, null);
    }

    public GameService(QuizRepository quizRepository, StageRepository stageRepository, SaveRepository saveRepository,
                       UserService userService) {
        this.quizRepository = quizRepository;
        this.stageRepository = stageRepository;
        this.saveRepository = saveRepository;
        this.userService = userService;
    }

    public void setUser(User user) {
        if (user == null) throw new IllegalArgumentException("사용자가 없습니다");
        if (!user.getNickname().equals(this.nickname)) {
            this.state = null;
            this.currentSlot = 0;
            this.currentQuiz = null;
            this.currentTarget = null;
        }
        this.user = user;
        this.nickname = user.getNickname();
    }

    public String getNickname() { return nickname; }

    private String requireNickname() {
        if (nickname == null) throw new IllegalStateException("닉네임이 정해지지 않았습니다");
        return nickname;
    }

    public void startNewGame() {
        Stage firstStage = stageRepository.findByNumber(FIRST_STAGE)
                .orElseThrow(() -> new IllegalStateException("1스테이지 파일이 없습니다"));
        startStage(firstStage, null);
        this.currentSlot = 0;
    }

    public void continueGame(int slot) {
        SaveData save = saveRepository.find(requireNickname(), slot)
                .orElseThrow(() -> new IllegalStateException(slot + "번 저장 칸이 비어 있습니다"));
        Stage stage = stageRepository.findByNumber(save.getNextStageNo())
                .or(() -> stageRepository.findByNumber(save.getClearedStage()))
                .orElseThrow(() -> new IllegalStateException("스테이지 파일이 없습니다"));
        // 저장된 점수, 아이템으로 시작
        startStage(stage, makeStageEntry(save.getScore(), save.getInventory()));
        this.currentSlot = slot;
    }

    public int autoSaveOnStageClear() {
        if (state == null) throw new IllegalStateException("진행 중인 게임이 없습니다");
        int targetSlot = resolveAutoSaveSlot();
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            int count = state.getItemCount(type);
            if (count > 0) inventory.put(type, count);
        }
        SaveData data = new SaveData(state.getStage().getNumber(), state.getHp(), state.getScore(), inventory,
                LocalDateTime.now());
        saveRepository.save(requireNickname(), targetSlot, data);
        this.currentSlot = targetSlot;
        if (userService != null) userService.recordStageClear(user, state.getScore());
        return targetSlot;
    }

    public void deleteSave(int slot) {
        saveRepository.delete(requireNickname(), slot);
        if (currentSlot == slot) currentSlot = 0;
    }

    private int resolveAutoSaveSlot() {
        if (currentSlot >= 1 && currentSlot <= GameConstants.SAVE_SLOT_COUNT) return currentSlot;
        int oldestSlot = 1;
        LocalDateTime oldestTime = null;
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            Optional<SaveData> existing = saveRepository.find(requireNickname(), slot);
            if (existing.isEmpty()) return slot;
            LocalDateTime savedAt = existing.get().getSavedAt();
            if (oldestTime == null || savedAt.isBefore(oldestTime)) {
                oldestTime = savedAt;
                oldestSlot = slot;
            }
        }
        return oldestSlot;
    }

    public List<Optional<SaveData>> getAllSaveSlots() {
        List<Optional<SaveData>> slots = new ArrayList<>(GameConstants.SAVE_SLOT_COUNT);
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            slots.add(nickname == null ? Optional.empty() : saveRepository.find(nickname, slot));
        }
        return slots;
    }

    public int getCurrentSlot() { return currentSlot; }

    // 태호 님 #26(미니맵)에서 가져옴
    /** 번호 순 전체 스테이지 목록(미니맵의 방 목록용). */
    public List<Stage> getAllStages() {
        // findAll()은 파일 하나가 잘못되면 전체가 실패하므로, 번호가 이어지는 동안만 하나씩 읽는다.
        List<Stage> stages = new ArrayList<>();
        for (int no = FIRST_STAGE; ; no++) {
            Optional<Stage> stage = stageRepository.findByNumber(no);
            if (stage.isEmpty()) {
                return stages;
            }
            stages.add(stage.get());
        }
    }

    public boolean hasStage(int stageNo) { return stageRepository.findByNumber(stageNo).isPresent(); }

    /** 다음 스테이지로 이동(점수, 아이템 유지, 체력 3, 열쇠는 다시 찾기) */
    public boolean loadStage(int stageNo) {
        Optional<Stage> nextStage = stageRepository.findByNumber(stageNo);
        if (nextStage.isEmpty()) return false;
        // 기존: 저장 슬롯에서 점수, 아이템 읽어옴 -> 저장 안 했으면 0이 돼서 지금 값으로 수정함.
        SaveData carryOver = null;
        if (state != null) {
            carryOver = makeStageEntry(state.getScore(), state.getInventorySnapshot());
        }
        startStage(nextStage.get(), carryOver);
        return true;
    }

    /** 현재 스테이지 처음부터 다시(체력 3, 점수와 아이템은 들어왔을 때로) */
    public boolean restartStage() {
        if (state == null) {
            return false;
        }
        // 같은 스테이지 다시 읽기
        Optional<Stage> stage = stageRepository.findByNumber(state.getStage().getNumber());
        if (stage.isEmpty()) {
            return false;
        }
        startStage(stage.get(), stageEntry);
        return true;
    }

    public boolean canRestartFromSave() { return currentSlot != 0; }
    public boolean restartFromSave() {
        if (!canRestartFromSave()) return false;
        continueGame(currentSlot);
        return true;
    }

    public GameState getState() { return state; }
    public Optional<Quiz> getCurrentQuiz() { return Optional.ofNullable(currentQuiz); }

    public GameObject.InteractionOutcome updatePlayer(double dt) {
        if (state == null || state.isDead() || state.isStageCleared()) return GameObject.InteractionOutcome.none();
        GameObject.InteractionOutcome outcome = state.getPlayer().update(dt, state);
        // 출구 들어왔는데 몬스터 남았거나 열쇠 없으면 클리어 취소
        if (outcome.type() == GameObject.EffectType.STAGE_CLEAR && !canClearStage()) {
            state.setStageCleared(false);
            // 출구에 서 있는 동안 계속 뜨지 않게
            suppressExitUntilLeave();
            return GameObject.InteractionOutcome.message(getClearBlockedMessage());
        }
        return outcome;
    }

    public Optional<GameObject> findNearbyInteractable() {
        if (state == null || state.isDead() || state.isStageCleared()) return Optional.empty();
        Player player = state.getPlayer();
        for (GameObject obj : state.getStage().getObjects()) {
            if (obj.canInteract(state) && player.overlaps(obj, state, obj.getInteractionRange())) return Optional.of(obj);
        }
        return Optional.empty();
    }

    public GameObject.InteractionOutcome interact(GameObject target) {
        if (state == null || target == null || state.isDead() || state.isStageCleared()) {
            return GameObject.InteractionOutcome.none();
        }
        // 열쇠 상자면 물약 대신 열쇠
        if (target.getType() == ObjectType.CHEST && target.getId().equals(keyChestId)) {
            return openKeyChest(target);
        }
        GameObject.InteractionOutcome outcome = target.onInteract(state);
        if (target.getType() == ObjectType.CHEST && outcome.type() != GameObject.EffectType.NONE) {
            AudioManager.getInstance().play(Sound.CHEST);
        }
        return outcome;
    }

    public boolean useItem(ItemType type) {
        if (state == null || type == null || state.isDead() || state.isStageCleared()) return false;
        return type.use(state);
    }

    /** 전투 시작(현재 스테이지 문제 중 랜덤) */
    public Optional<Quiz> openQuiz(GameObject source) {
        // 게임 시작 안됨 or 체력이 0 or 퀴즈를 낼 오브젝트가 없음 or 이미 해결한 오브젝트면, 퀴즈를 열지 않음
        if (state == null || state.isDead() || source == null || state.isObjectResolved(source.getId())) return Optional.empty();
        // 기존: quizId로 문제 찾음 -> 노션에서 그 칸이 적 HP로 바뀌어서 랜덤으로 수정함.
        Quiz quiz = pickQuiz();
        if (quiz == null) {
            return Optional.empty();
        }
        // 처음 싸우는 적이면 HP 채우기
        if (!enemyHp.containsKey(source.getId())) {
            enemyHp.put(source.getId(), getEnemyMaxHp(source));
        }
        this.currentQuiz = quiz;
        this.currentTarget = source;
        return Optional.of(quiz);
    }

    /** 정답이면 적 HP -1, 오답이면 플레이어 HP -1 */
    public AnswerResult submitAnswer(String input) {
        if (currentQuiz == null) throw new IllegalStateException("열린 퀴즈가 없습니다");
        // 체력 0이라 답 못 하는 상황
        if (state.isDead()) {
            throw new IllegalStateException("게임 오버 상태입니다");
        }
        // 경고 팝업에 대답 안 하고 답 내면 계속 전투로 봄
        askingPotion = false;
        boolean correct = currentQuiz.isCorrect(input);
        String explanation = currentQuiz.getExplanation();
        if (correct) {
            AudioManager audio = AudioManager.getInstance();
            audio.play(Sound.CORRECT);
            int leftHp = damageEnemy(currentTarget);

            // 적 HP 남음 -> 다음 문제
            if (leftHp > 0) {
                audio.play(Sound.ATTACK);
                currentQuiz = pickQuiz();
                // 매개변수: 정답, 해설, 얻은 점수, 받은 데미지, 사망, 적 데미지, 적 처치
                // 뒤에 두 개는 이름만 boss고 모든 적에 씀
                return new AnswerResult(true, explanation, 0, 0, false, ENEMY_DAMAGE, false);
            }

            // 적 HP 0 -> 처치
            int scoreBefore = state.getScore();
            if (currentTarget != null) currentTarget.onQuizSolved(state);
            int gainedScore = state.getScore() - scoreBefore;
            if (currentTarget != null && (currentTarget.getType() == ObjectType.MONSTER
                    || currentTarget.getType() == ObjectType.BOSS)) {
                audio.play(Sound.ATTACK);
                audio.play(Sound.VICTORY);
            }
            currentQuiz = null;
            currentTarget = null;
            return new AnswerResult(true, explanation, gainedScore, 0, false, ENEMY_DAMAGE, true);
        }
        return applyWrongAnswer(explanation);
    }

    private AnswerResult applyWrongAnswer(String explanation) {
        state.damage(WRONG_ANSWER_DAMAGE);
        AudioManager.getInstance().play(Sound.WRONG);
        AudioManager.getInstance().play(Sound.HIT);
        boolean isDead = state.isDead();
        if (isDead) AudioManager.getInstance().play(Sound.DEFEAT);
        // 죽었으면 미니게임 후 이 문제로 돌아와야 해서 안 바꿈
        if (!isDead) {
            currentQuiz = pickQuiz();
        }
        // HP 1 됐는데 물약 있으면 쓸지 물어봄
        if (canUseHealItem()) {
            askingPotion = true;
        }
        return new AnswerResult(false, explanation, 0, WRONG_ANSWER_DAMAGE, isDead, 0, false);
    }

    public AnswerResult timeOut() {
        if (currentQuiz == null) throw new IllegalStateException("열린 퀴즈가 없습니다");
        // 체력 0이라 답 못 하는 상황
        if (state.isDead()) {
            throw new IllegalStateException("게임 오버 상태입니다");
        }
        // 경고 팝업에 대답 안 하고 답 내면 계속 전투로 봄
        askingPotion = false;
        return applyWrongAnswer(currentQuiz.getExplanation());
    }

    public void cancelQuiz() {
        if (state != null && currentTarget != null) state.getPlayer().suppressObjectUntilLeave(currentTarget.getId());
        currentQuiz = null;
        currentTarget = null;
        askingPotion = false;
    }

    /** 물약 쓸지 물어보는 중이면 true(HP 1 경고 팝업 띄울 때) */
    public boolean isAskingPotion() {
        return askingPotion;
    }

    /** HP 1 경고 팝업 대답(회복 아이템 사용 -> 물약 1개 쓰고 HP 2, 계속 전투 -> HP 1 그대로) 물약 썼으면 true */
    public boolean answerPotion(boolean use) {
        if (!askingPotion) {
            throw new IllegalStateException("물약을 물어보는 중이 아닙니다");
        }
        askingPotion = false;
        if (use) {
            return useHealItem().success();
        }
        return false;
    }

    /** 지금 싸우는 적 남은 HP(전투 화면용) */
    public int getEnemyHp() {
        if (currentTarget == null) {
            return 0;
        }
        return getEnemyHp(currentTarget);
    }

    /** 지금 싸우는 적 최대 HP(전투 화면용) */
    public int getEnemyMaxHp() {
        if (currentTarget == null) {
            return 0;
        }
        return getEnemyMaxHp(currentTarget);
    }

    /** 적별 남은 HP(저장용) */
    public Map<String, Integer> getEnemyHpSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(enemyHp));
    }

    /** 열쇠 있음/없음 */
    public boolean hasKey() {
        return state != null && state.getItemCount(ItemType.KEY) > 0;
    }

    /** 열쇠 상자 ID(저장용) */
    public String getKeyChestId() {
        return keyChestId;
    }

    /** 몬스터 다 잡았는지(미믹, 함정은 상관없음) */
    public boolean areAllMonstersDefeated() {
        if (state == null) {
            return false;
        }
        for (GameObject obj : state.getStage().getObjects()) {
            if (obj.getType() == ObjectType.MONSTER && !state.isObjectResolved(obj.getId())) {
                return false;
            }
        }
        return true;
    }

    /** 클리어 조건: 몬스터 다 잡기 + 열쇠 */
    public boolean canClearStage() {
        return areAllMonstersDefeated() && hasKey();
    }

    /** 물약 쓴 결과: 성공 여부, 지금 HP, 남은 물약 수 */
    public record HealResult(boolean success, int hp, int potionCount) {
    }

    /** 물약 있음/없음 */
    public boolean hasHealItem() {
        return state != null && state.getItemCount(ItemType.HEALTH_POTION) > 0;
    }

    /** 물약 쓰기(1개 쓰고 HP +1, HP 꽉 찼거나 물약 없으면 아무것도 안 바뀜) */
    public HealResult useHealItem() {
        if (state == null) {
            return new HealResult(false, 0, 0);
        }
        boolean success = useItem(ItemType.HEALTH_POTION);
        return new HealResult(success, state.getHp(), state.getItemCount(ItemType.HEALTH_POTION));
    }

    /** HP 1이고 물약 있으면 true(회복 안내용) */
    public boolean canUseHealItem() {
        if (state == null || state.isDead()) {
            return false;
        }
        return state.getHp() == 1 && state.getItemCount(ItemType.HEALTH_POTION) > 0;
    }

    /** 미니게임 성공하면 회복하고 하던 전투로 */
    public boolean reviveAfterMinigame() {
        if (state == null || !state.isDead()) {
            return false;
        }
        state.heal(MINIGAME_HEAL);
        return true;
    }

    /** 스테이지 시작할 때 공통 */
    private void startStage(Stage stage, SaveData entry) {
        // state 바꾸기 전에 해야 함
        cancelQuiz();
        this.stageEntry = entry;
        this.state = new GameState(stage, MAX_HP, entry);
        enemyHp.clear();
        this.keyChestId = pickKeyChest(stage);
    }

    /** 스테이지 들어올 때 상태 만들기(열쇠는 뺌) */
    private SaveData makeStageEntry(int score, Map<ItemType, Integer> inventory) {
        Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
        for (ItemType type : inventory.keySet()) {
            // 열쇠는 스테이지마다 다시 찾아야 함
            if (type != ItemType.KEY && inventory.get(type) > 0) {
                items.put(type, inventory.get(type));
            }
        }
        // 매개변수: 깬 스테이지(여기선 안 씀), 체력, 점수, 아이템, 저장 시간
        return new SaveData(SaveData.NONE_CLEARED, MAX_HP, score, items, LocalDateTime.now());
    }

    /** 현재 스테이지 문제 랜덤으로 뽑기 */
    private Quiz pickQuiz() {
        int stageNo = state.getStage().getNumber();
        List<Quiz> candidates = new ArrayList<>();
        for (Quiz quiz : quizRepository.findAll()) {
            if (quiz.getStageNo() == stageNo) {
                candidates.add(quiz);
            }
        }
        // 이 스테이지 문제 없으면 전체에서
        if (candidates.isEmpty()) {
            candidates.addAll(quizRepository.findAll());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // 방금 낸 문제 또 안 나오게
        if (currentQuiz != null && candidates.size() > 1) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getId() == currentQuiz.getId()) {
                    candidates.remove(i);
                    break;
                }
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 일반 상자 중 하나 랜덤으로 열쇠 상자(미믹 제외) */
    private String pickKeyChest(Stage stage) {
        List<String> chestIds = new ArrayList<>();
        for (GameObject obj : stage.getObjects()) {
            if (obj.getType() == ObjectType.CHEST) {
                chestIds.add(obj.getId());
            }
        }
        if (chestIds.isEmpty()) {
            return null;
        }
        return chestIds.get(random.nextInt(chestIds.size()));
    }

    /** 열쇠 상자 열기 */
    private GameObject.InteractionOutcome openKeyChest(GameObject chest) {
        if (state.isObjectResolved(chest.getId())) {
            return GameObject.InteractionOutcome.none();
        }
        state.markObjectResolved(chest.getId());
        state.addItem(ItemType.KEY, 1);
        state.addScore(KEY_CHEST_SCORE);
        AudioManager.getInstance().play(Sound.CHEST);
        return GameObject.InteractionOutcome.message("상자에서 열쇠 획득!");
    }

    /** 적 최대 HP */
    private int getEnemyMaxHp(GameObject enemy) {
        // 파일 5번째 칸이 적 HP인데 로더에서 이름이 아직 quizId임
        int maxHp = enemy.getQuizId();
        if (maxHp <= 0) {
            return DEFAULT_ENEMY_HP;
        }
        return maxHp;
    }

    /** 적 남은 HP(안 싸운 적이면 최대 HP) */
    private int getEnemyHp(GameObject enemy) {
        Integer left = enemyHp.get(enemy.getId());
        if (left == null) {
            return getEnemyMaxHp(enemy);
        }
        return left;
    }

    /** 적 HP 깎고 남은 HP 돌려줌 */
    private int damageEnemy(GameObject enemy) {
        if (enemy == null) {
            return 0;
        }
        int left = Math.max(0, getEnemyHp(enemy) - ENEMY_DAMAGE);
        enemyHp.put(enemy.getId(), left);
        return left;
    }

    /** 출구 벗어날 때까지 다시 검사 안 함 */
    private void suppressExitUntilLeave() {
        Player player = state.getPlayer();
        for (GameObject obj : state.getStage().getObjects()) {
            if (obj.getType() == ObjectType.EXIT && player.overlaps(obj, state, 0.0)) {
                player.suppressObjectUntilLeave(obj.getId());
                return;
            }
        }
    }

    /** 클리어 안 될 때 안내 문구 */
    private String getClearBlockedMessage() {
        if (!areAllMonstersDefeated() && !hasKey()) {
            return "몬스터를 모두 처치하고 열쇠를 찾아야 합니다";
        }
        if (!areAllMonstersDefeated()) {
            return "남은 몬스터를 모두 처치해야 합니다";
        }
        return "열쇠가 필요합니다";
    }
}
