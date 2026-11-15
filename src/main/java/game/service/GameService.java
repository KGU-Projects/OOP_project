package game.service;

import game.model.Stage;
import game.model.GameState;
import game.model.User;
import game.repository.QuizRepository;
import game.repository.StageRepository;
import game.repository.SaveRepository;
import game.model.quiz.Quiz;
import game.model.object.GameObject;

import java.util.Optional;
import game.model.object.ButtonObject;
import game.model.AnswerResult;
import game.model.SaveData;


/** UI와 데이터 사이 연결 및 게임 규칙 처리 */
public class GameService {
    private static final int FIRST_STAGE = 1;
    /** 플레이어 최대 체력 (TODO: 확정 전 임시값, 3으로 수정될 수도. 일단 UI에서 임시로 쓰는 5로 맞춤) */
    private static final int MAX_HP = 5;
    private static final int QUIZ_SCORE = 100;
    private static final int WRONG_ANSWER_DAMAGE = 1;

    private final QuizRepository quizRepository;
    private final StageRepository stageRepository;
    private final SaveRepository saveRepository;
    private GameState state;
    private User currentUser;
    /** 이번 게임에서 마지막으로 불러오거나 저장한 칸 번호. 0이면 없음 */
    private int currentSlot;
    private Quiz currentQuiz;
    /** 그 문제를 낸 오브젝트(버튼 등). 퀴즈가 안 열려 있으면 null */
    private GameObject currentTarget;

    /** 게임 시작할 수 있게 데이터 받아오기*/
    public GameService(QuizRepository quizRepository, StageRepository stageRepository, SaveRepository saveRepository) {
        this.quizRepository = quizRepository;
        this.stageRepository = stageRepository;
        this.saveRepository = saveRepository;
    }

    /** 새 게임: 1스테이지를 처음 상태로 시작하기 */
    public void startNewGame(User user) {
        Stage firstStage = stageRepository.findByNumber(FIRST_STAGE)
                    .orElseThrow(() -> new IllegalStateException("1스테이지 파일이 없습니다"));
        this.currentUser = user;
        this.state = new GameState(firstStage, MAX_HP, null);
        this.currentSlot = 0;
        cancelQuiz();   // 이전 게임에서 열려 있던 퀴즈 메모 비우기
    }

    /** 불러오기: 마지막으로 깬 스테이지의 다음 스테이지 시작하기 */
    public void continueGame(User user, int slot) {
        SaveData save = saveRepository.find(user.getNickname(), slot)
                .orElseThrow(() -> new IllegalStateException(slot + "번 저장 칸이 비어 있습니다"));
        Stage stage = stageRepository.findByNumber(save.getNextStageNo())
                .orElseThrow(() -> new IllegalStateException("다음 스테이지 파일이 없습니다"));
        this.state = new GameState(stage, MAX_HP, save);
        this.currentUser = user;
        this.currentSlot = slot;
        cancelQuiz();   // 이전 게임에서 열려 있던 퀴즈 메모 비우기
    }

    /** 게임 오버 시: 저장했다면? true. 마지막 저장 스테이지로 돌아가기 가능한지 확인 */
    public boolean canRestartFromSave() {
        return currentSlot != 0;
    }

    /** 게임 오버 시 저장: 마지막으로 쓴 스테이지 다시 불러오기 */
    public boolean restartFromSave() {
        if (!canRestartFromSave()) {
            return false;
        }
        continueGame(currentUser, currentSlot);
        return true;
    }

    /**현재 게임 상태, UI에서 사용 */
    public GameState getState() {
        return state;
    }

    /** 버튼이랑 퀴즈 연결되어있는 거 열고, UI에 전달. 이미 푼 버튼이거나 문제가 없으면 빈 상자 */
    public Optional<Quiz> openQuiz(ButtonObject button) {
        String id = button.getId();
        // 푼 버튼인지 확인
        if (state.isButtonSolved(id)) {
            return Optional.empty();
        }
        // 버튼이랑 연결된 문제
        int quizId = button.getQuizId();
        Optional<Quiz> quiz = quizRepository.findById(quizId);
        if (quiz.isEmpty()) {
            return Optional.empty();
        }
        
        // submitAnswer(답만 앎)에서 문제랑 연결된 버튼 알기 위함
        this.currentQuiz = quiz.get();
        this.currentTarget = button;
        // UI에 퀴즈 뭔지 전달
        return quiz;
    }

    /** 열린 퀴즈에 답 제출 후 채점 결과 UI에 전달 */
    public AnswerResult submitAnswer(String input) {
        if (currentQuiz == null) {
            throw new IllegalStateException("열린 퀴즈가 없습니다");
        }
        boolean correct = currentQuiz.isCorrect(input);
        String explanation = currentQuiz.getExplanation();

        if (correct) {
            state.addScore(QUIZ_SCORE);
            // 퀴즈 맞히면 문 열려야 됨. 버튼에 연결된 문 열기
            // TODO: 근데 한 스테이지에 문이 여러 개? 나중에 확인 필요
            for (String doorId : currentTarget.getLinkedIds()) {
                state.openDoor(doorId);
            }

            // 푼 문제는 다시 퀴즈 안 나오게 푼 버튼으로 기록
            state.markButtonSolved(currentTarget.getId());
            // 퀴즈 끝났으니까 메모 지우기
            currentQuiz = null;
            currentTarget = null;

            // 일반 버튼 퀴즈 정답 결과 (보스는 최종 스테이지에만 나오는 지금 설계 기준)
            // TODO: 보스 칸은 0, false. 보스전은 나중에 추가
            // TODO: 문제 풀 때마다 나오는 몬스터 버전으로 바뀌면 수정 필요
            return new AnswerResult(true, explanation, QUIZ_SCORE, 0, false, 0, false);
        }
        // 오답: 체력 깎고 체력 0인지 확인(퀴즈는 열린 채로 두기. 다시 도전 가능)
        state.damage(WRONG_ANSWER_DAMAGE);
        boolean isDead = state.isDead();
        return new AnswerResult(false, explanation, 0, WRONG_ANSWER_DAMAGE, isDead, 0, false);
    }

    /** 퀴즈 취소: 열린 퀴즈 정보만 지우고 맵으로 */
    public void cancelQuiz() {
        currentQuiz = null;
        currentTarget = null;
    }
}