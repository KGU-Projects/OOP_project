package game.service;

import game.common.GameConstants;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.Player;
import game.model.SaveData;
import game.model.Stage;
import game.model.object.GameObject;
import game.model.quiz.Quiz;
import game.repository.QuizRepository;
import game.repository.SaveRepository;
import game.repository.StageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameService {
    private static final int FIRST_STAGE = 1;
    private static final int MAX_HP = 3;
    private static final int WRONG_ANSWER_DAMAGE = 1;
    private static final String DEFAULT_NICKNAME = "Player";

    private final QuizRepository quizRepository;
    private final StageRepository stageRepository;
    private final SaveRepository saveRepository;
    private GameState state;
    private int currentSlot;
    private Quiz currentQuiz;
    private GameObject currentTarget;

    public GameService(QuizRepository quizRepository, StageRepository stageRepository, SaveRepository saveRepository) {
        this.quizRepository = quizRepository;
        this.stageRepository = stageRepository;
        this.saveRepository = saveRepository;
    }

    public void startNewGame() {
        Stage firstStage = stageRepository.findByNumber(FIRST_STAGE)
                .orElseThrow(() -> new IllegalStateException("1스테이지 파일이 없습니다"));
        this.state = new GameState(firstStage, MAX_HP, null);
        this.currentSlot = 0;
        cancelQuiz();
    }

    public void continueGame(int slot) {
        SaveData save = saveRepository.find(DEFAULT_NICKNAME, slot)
                .orElseThrow(() -> new IllegalStateException(slot + "번 저장 칸이 비어 있습니다"));
        Stage stage = stageRepository.findByNumber(save.getNextStageNo())
                .or(() -> stageRepository.findByNumber(save.getClearedStage()))
                .orElseThrow(() -> new IllegalStateException("스테이지 파일이 없습니다"));
        this.state = new GameState(stage, MAX_HP, save);
        this.currentSlot = slot;
        cancelQuiz();
    }

    public int autoSaveOnStageClear() {
        if (state == null) {
            throw new IllegalStateException("진행 중인 게임이 없습니다");
        }
        int targetSlot = resolveAutoSaveSlot();

        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            int count = state.getItemCount(type);
            if (count > 0) {
                inventory.put(type, count);
            }
        }

        SaveData data = new SaveData(
                state.getStage().getNumber(),
                state.getHp(),
                state.getScore(),
                inventory,
                LocalDateTime.now());
        saveRepository.save(DEFAULT_NICKNAME, targetSlot, data);
        this.currentSlot = targetSlot;
        return targetSlot;
    }

    public void deleteSave(int slot) {
        saveRepository.delete(DEFAULT_NICKNAME, slot);
        if (currentSlot == slot) {
            currentSlot = 0;
        }
    }

    private int resolveAutoSaveSlot() {
        if (currentSlot >= 1 && currentSlot <= GameConstants.SAVE_SLOT_COUNT) {
            return currentSlot;
        }
        int oldestSlot = 1;
        LocalDateTime oldestTime = null;

        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            Optional<SaveData> existing = saveRepository.find(DEFAULT_NICKNAME, slot);
            if (existing.isEmpty()) {
                return slot;
            }
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
            slots.add(saveRepository.find(DEFAULT_NICKNAME, slot));
        }
        return slots;
    }

    public int getCurrentSlot() {
        return currentSlot;
    }

    public boolean hasStage(int stageNo) {
        return stageRepository.findByNumber(stageNo).isPresent();
    }

    public boolean loadStage(int stageNo) {
        Optional<Stage> nextStage = stageRepository.findByNumber(stageNo);
        if (nextStage.isEmpty()) {
            return false;
        }
        SaveData carryOver = (currentSlot != 0)
                ? saveRepository.find(DEFAULT_NICKNAME, currentSlot).orElse(null)
                : null;
        this.state = new GameState(nextStage.get(), MAX_HP, carryOver);
        cancelQuiz();
        return true;
    }

    public boolean canRestartFromSave() {
        return currentSlot != 0;
    }

    public boolean restartFromSave() {
        if (!canRestartFromSave()) {
            return false;
        }
        continueGame(currentSlot);
        return true;
    }

    public GameState getState() {
        return state;
    }

    public Optional<Quiz> getCurrentQuiz() {
        return Optional.ofNullable(currentQuiz);
    }

    public GameObject.InteractionOutcome updatePlayer(double dt) {
        if (state == null || state.isDead() || state.isStageCleared()) {
            return GameObject.InteractionOutcome.none();
        }
        return state.getPlayer().update(dt, state);
    }

    public Optional<GameObject> findNearbyInteractable() {
        if (state == null || state.isDead() || state.isStageCleared()) {
            return Optional.empty();
        }
        Player player = state.getPlayer();
        for (GameObject obj : state.getStage().getObjects()) {
            if (obj.canInteract(state) && player.overlaps(obj, state, obj.getInteractionRange())) {
                return Optional.of(obj);
            }
        }
        return Optional.empty();
    }

    public GameObject.InteractionOutcome interact(GameObject target) {
        if (state == null || target == null || state.isDead() || state.isStageCleared()) {
            return GameObject.InteractionOutcome.none();
        }
        return target.onInteract(state);
    }

    public boolean useItem(ItemType type) {
        if (state == null || type == null || state.isDead() || state.isStageCleared()) {
            return false;
        }
        return type.use(state);
    }

    public Optional<Quiz> openQuiz(GameObject source) {
        if (state == null || source == null || state.isObjectResolved(source.getId())) {
            return Optional.empty();
        }
        int quizId = source.getQuizId();
        if (quizId <= 0) {
            return Optional.empty();
        }
        Optional<Quiz> quiz = quizRepository.findById(quizId);
        if (quiz.isEmpty()) {
            return Optional.empty();
        }

        this.currentQuiz = quiz.get();
        this.currentTarget = source;
        return quiz;
    }

    public AnswerResult submitAnswer(String input) {
        if (currentQuiz == null) {
            throw new IllegalStateException("열린 퀴즈가 없습니다");
        }
        boolean correct = currentQuiz.isCorrect(input);
        String explanation = currentQuiz.getExplanation();

        if (correct) {
            int scoreBefore = state.getScore();
            if (currentTarget != null) {
                currentTarget.onQuizSolved(state);
            }
            int gainedScore = state.getScore() - scoreBefore;
            currentQuiz = null;
            currentTarget = null;

            return new AnswerResult(true, explanation, gainedScore, 0, false, 1, true);
        }
        return applyWrongAnswer(explanation);
    }

    private AnswerResult applyWrongAnswer(String explanation) {
        state.damage(WRONG_ANSWER_DAMAGE);
        boolean isDead = state.isDead();
        return new AnswerResult(false, explanation, 0, WRONG_ANSWER_DAMAGE, isDead, 0, false);
    }

    public AnswerResult timeOut() {
        if (currentQuiz == null) {
            throw new IllegalStateException("열린 퀴즈가 없습니다");
        }
        return applyWrongAnswer(currentQuiz.getExplanation());
    }

    public void cancelQuiz() {
        if (state != null && currentTarget != null) {
            state.getPlayer().suppressObjectUntilLeave(currentTarget.getId());
        }
        currentQuiz = null;
        currentTarget = null;
    }
}