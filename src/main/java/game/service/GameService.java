package game.service;

import game.common.GameConstants;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.Player;
import game.model.SaveData;
import game.model.Stage;
import game.model.User;
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

/** UI와 데이터 사이 연결 및 게임 규칙 처리 */
public class GameService {
    private static final int FIRST_STAGE = 1;
    private static final int MAX_HP = 3;
    private static final int WRONG_ANSWER_DAMAGE = 1;
    private static final String DEFAULT_NICKNAME = "Player";

    private final QuizRepository quizRepository;
    private final StageRepository stageRepository;
    private final SaveRepository saveRepository;
    private GameState state;
    private User currentUser;
    private int currentSlot;
    private Quiz currentQuiz;
    private GameObject currentTarget;

    public GameService(QuizRepository quizRepository, StageRepository stageRepository, SaveRepository saveRepository) {
        this.quizRepository = quizRepository;
        this.stageRepository = stageRepository;
        this.saveRepository = saveRepository;
    }

    public void startNewGame(User user) {
        Stage firstStage = stageRepository.findByNumber(FIRST_STAGE)
                .orElseThrow(() -> new IllegalStateException("1스테이지 파일이 없습니다"));
        this.currentUser = (user != null) ? user : User.createNew(DEFAULT_NICKNAME, LocalDateTime.now());
        this.state = new GameState(firstStage, MAX_HP, null);
        this.currentSlot = 0;
        cancelQuiz();
    }

    public void continueGame(User user, int slot) {
        User resolvedUser = (user != null) ? user : getCurrentUser();
        SaveData save = saveRepository.find(resolvedUser.getNickname(), slot)
                .orElseThrow(() -> new IllegalStateException(slot + "번 저장 칸이 비어 있습니다"));
        Stage stage = stageRepository.findByNumber(save.getNextStageNo())
                .orElseThrow(() -> new IllegalStateException("다음 스테이지 파일이 없습니다"));
        this.state = new GameState(stage, MAX_HP, save);
        this.currentUser = resolvedUser;
        this.currentSlot = slot;
        cancelQuiz();
    }

    public SaveData saveGame(int slot) {
        if (state == null) {
            throw new IllegalStateException("진행 중인 게임이 없습니다");
        }
        User user = getCurrentUser();
        int currentStageNo = state.getStage().getNumber();
        int clearedStage = state.isStageCleared()
                ? currentStageNo
                : Math.max(0, currentStageNo - 1);

        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            int count = state.getItemCount(type);
            if (count > 0) {
                inventory.put(type, count);
            }
        }

        SaveData data = new SaveData(
                clearedStage,
                state.getHp(),
                state.getScore(),
                inventory,
                LocalDateTime.now());
        saveRepository.save(user.getNickname(), slot, data);
        this.currentSlot = slot;
        return data;
    }

    public List<Optional<SaveData>> getAllSaveSlots(String nickname) {
        String target = (nickname == null || nickname.isBlank()) ? getCurrentUser().getNickname() : nickname;
        List<Optional<SaveData>> slots = new ArrayList<>(GameConstants.SAVE_SLOT_COUNT);
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            slots.add(saveRepository.find(target, slot));
        }
        return slots;
    }

    public User getCurrentUser() {
        if (currentUser == null) {
            currentUser = User.createNew(DEFAULT_NICKNAME, LocalDateTime.now());
        }
        return currentUser;
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
        this.state = new GameState(nextStage.get(), MAX_HP, null);
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
        continueGame(currentUser, currentSlot);
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

    /** 몬스터·미믹·함정 등 퀴즈를 가진 오브젝트와 연결된 문제를 연다. */
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

    /** 퀴즈 취소: 중단한 오브젝트는 플레이어가 한 번 벗어날 때까지 재발동을 억제한다. */
    public void cancelQuiz() {
        if (state != null && currentTarget != null) {
            state.getPlayer().suppressObjectUntilLeave(currentTarget.getId());
        }
        currentQuiz = null;
        currentTarget = null;
    }
}