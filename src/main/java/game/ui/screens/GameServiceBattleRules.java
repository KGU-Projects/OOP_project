package game.ui.screens;

import game.model.GameState;
import game.model.quiz.Quiz;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@link QuizBattleController.BattleRules}를 GameService에 연결하는 어댑터.
 *
 * 판정, HP·점수·몬스터 상태 변경은 전부 GameService가 하고, 이 클래스는 호출을 넘기고 결과 값을 옮겨 담기만 한다.
 * HP는 판정 뒤 GameState에서 다시 읽으므로 화면과 게임 상태가 어긋나거나 HP가 두 번 바뀌지 않는다.
 *
 * GameService와 AnswerResult는 아직 main에 없어서(feat/game-service) 이 클래스는 그 타입을 직접 쓰지 않고
 * 메서드 참조로 받는다. 연결 예:
 * <pre>
 * BattleRules rules = new GameServiceBattleRules&lt;&gt;(
 *         gameService::getState,
 *         () -&gt; gameService.openQuiz(button),      // 몬스터 전투 API가 생기면 그 메서드로 교체
 *         gameService::submitAnswer,
 *         r -&gt; new QuizBattleController.Judgement(r.isCorrect(), r.getDamageTaken(),
 *                 r.getBossDamage(), r.isPlayerDead(), r.isBossDefeated()),
 *         gameService::cancelQuiz);
 * </pre>
 *
 * @param <R> GameService.submitAnswer의 결과 타입 (AnswerResult)
 */
public final class GameServiceBattleRules<R> implements QuizBattleController.BattleRules {

    private final Supplier<GameState> state;
    private final Supplier<Optional<Quiz>> quizSource;
    private final Function<String, R> submitAnswer;
    private final Function<R, QuizBattleController.Judgement> toJudgement;
    private final Runnable cancelQuiz;
    /** 회복 아이템 사용 연동 지점 (null이면 연결 전). 사용 성공 여부를 돌려준다. */
    private BooleanSupplier recoveryItemUse;
    /** 실제 적 HP 연동 (null이면 GameState의 보스 값을 쓴다) */
    private IntSupplier enemyHp;
    private IntSupplier enemyMaxHp;
    /** HP 1 회복 질문을 엔진이 관리하는 경우의 연동 (null이면 컨트롤러가 직접 경고) */
    private BooleanSupplier recoveryAsking;
    private Predicate<Boolean> recoveryAnswer;
    /** 미니게임 성공 시 되살리기 연동 (null이면 미지원: 미니게임으로 나가면 열린 퀴즈를 정리한다) */
    private BooleanSupplier reviveAfterMinigame;
    private Supplier<R> timeOut;

    /**
     * @param state        GameService.getState
     * @param quizSource   다음 문제를 여는 GameService 호출 (지금은 openQuiz(버튼)). 열 문제가 없으면 empty
     * @param submitAnswer GameService.submitAnswer
     * @param toJudgement  submitAnswer 결과를 Judgement로 옮겨 담는 함수 (값 복사만)
     * @param cancelQuiz   GameService.cancelQuiz. 화면을 떠날 때 열린 퀴즈를 정리한다
     */
    public GameServiceBattleRules(Supplier<GameState> state,
                                  Supplier<Optional<Quiz>> quizSource,
                                  Function<String, R> submitAnswer,
                                  Function<R, QuizBattleController.Judgement> toJudgement,
                                  Runnable cancelQuiz) {
        if (state == null || quizSource == null || submitAnswer == null || toJudgement == null || cancelQuiz == null) {
            throw new IllegalArgumentException("GameService 연결 값은 null일 수 없습니다");
        }
        this.state = state;
        this.quizSource = quizSource;
        this.submitAnswer = submitAnswer;
        this.toJudgement = toJudgement;
        this.cancelQuiz = cancelQuiz;
    }

    @Override
    public Optional<Quiz> nextQuiz() {
        Optional<Quiz> quiz = quizSource.get();
        return quiz == null ? Optional.empty() : quiz;
    }

    @Override
    public QuizBattleController.Judgement submitAnswer(Quiz quiz, String answer) {
        return toJudgement.apply(submitAnswer.apply(answer));
    }

    /**
     * 답 없이 제한시간이 끝난 경우. {@link #withTimeOut}으로 GameService.timeOut이 연결되어 있으면 그것을 호출하고,
     * 아니면 빈 답을 제출해 GameService의 오답 규칙을 그대로 따른다.
     * 제한시간을 쓰지 않으려면 컨트롤러에 {@link QuizScreen#NO_TIME_LIMIT}을 준다.
     */
    @Override
    public QuizBattleController.Judgement timeOut(Quiz quiz) {
        if (timeOut != null) {
            return toJudgement.apply(timeOut.get()); // 엔진의 시간 초과 규칙 (GameService.timeOut)
        }
        return toJudgement.apply(submitAnswer.apply(""));
    }

    /** 시간 초과 연동 (GameService.timeOut). 연결하지 않으면 위처럼 빈 답을 제출한다. */
    public GameServiceBattleRules<R> withTimeOut(Supplier<R> timeOutCall) {
        this.timeOut = timeOutCall;
        return this;
    }

    /**
     * 플레이어 HP는 GameState 값이다. 몬스터 HP는 {@link #withEnemyHp}로 실제 적 HP(GameService.getEnemyHp/getEnemyMaxHp)가
     * 연결되어 있으면 그 값을, 아니면 GameState의 보스 값을 쓴다. 가짜 값을 만들지 않는다.
     */
    @Override
    public QuizBattleController.HpView hp() {
        GameState s = state.get();
        if (enemyHp != null && enemyMaxHp != null) {
            return new QuizBattleController.HpView(s.getHp(), s.getMaxHp(), enemyHp.getAsInt(), enemyMaxHp.getAsInt());
        }
        return new QuizBattleController.HpView(s.getHp(), s.getMaxHp(), s.getBossHp(), s.getBossMaxHp());
    }

    /**
     * 실제 적 HP 연동 (GameService.getEnemyHp, getEnemyMaxHp). 연결하면 몬스터 하트가 엔진의 값으로 표시된다.
     * 마지막 정답으로 적이 쓰러져 최대 HP가 0으로 조회되어도 화면은 이번 전투의 최대 HP를 유지한다.
     */
    public GameServiceBattleRules<R> withEnemyHp(IntSupplier hp, IntSupplier maxHp) {
        this.enemyHp = hp;
        this.enemyMaxHp = maxHp;
        return this;
    }

    /**
     * HP 1 회복 질문 연동 (GameService.isAskingPotion, answerPotion). 연결하면 엔진이 물을 때만 경고 팝업이 뜨고,
     * 선택(회복 아이템 사용 / 계속 싸우기)은 answerPotion으로 엔진에 돌려줘 질문 상태가 풀린다.
     * answer는 아이템을 실제로 사용했으면 true를 돌려준다.
     */
    public GameServiceBattleRules<R> withRecoveryPrompt(BooleanSupplier asking, Predicate<Boolean> answer) {
        this.recoveryAsking = asking;
        this.recoveryAnswer = answer;
        return this;
    }

    /**
     * 미니게임 성공 연동 (GameService.reviveAfterMinigame). 연결하면 GAME OVER에서 미니게임으로 나가도 열린 퀴즈를 정리하지
     * 않으므로, 성공 뒤 {@link QuizBattleLauncher#resumeAfterMinigame()}으로 같은 문제에 돌아올 수 있다.
     */
    public GameServiceBattleRules<R> withMinigameRevive(BooleanSupplier revive) {
        this.reviveAfterMinigame = revive;
        return this;
    }

    @Override
    public boolean managesRecoveryPrompt() {
        return recoveryAsking != null && recoveryAnswer != null;
    }

    @Override
    public boolean isRecoveryPromptOpen() {
        return managesRecoveryPrompt() && recoveryAsking.getAsBoolean();
    }

    @Override
    public boolean answerRecoveryPrompt(boolean use) {
        return managesRecoveryPrompt() && recoveryAnswer.test(use);
    }

    @Override
    public boolean reviveAfterMinigame() {
        return reviveAfterMinigame != null && reviveAfterMinigame.getAsBoolean();
    }

    /** 지금 플레이 중인 스테이지 번호 (GameState). 알 수 없으면 0 */
    @Override
    public int stageNumber() {
        try {
            return state.get().getStage().getNumber();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * 회복 아이템 사용 연동 지점. GameService에 아이템 소비 API(HP +1, 포션 1개 소모)가 생기면
     * 그 호출을 여기에 넘긴다 (예: gameService::useHealthPotion). 연결하기 전에는 아이템이 있어도
     * 사용할 수 없으므로 HP 경고 팝업이 뜨지 않는다.
     */
    public GameServiceBattleRules<R> withRecoveryItemUse(BooleanSupplier use) {
        this.recoveryItemUse = use;
        return this;
    }

    /** 회복 아이템(HEALTH_POTION)이 있고 사용 연동도 연결되어 있는지. 확인 중 예외가 나면 없는 것으로 본다. */
    @Override
    public boolean hasRecoveryItem() {
        if (recoveryItemUse == null) {
            return false;
        }
        try {
            return state.get().getItemCount(game.model.ItemType.HEALTH_POTION) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean useRecoveryItem() {
        return recoveryItemUse != null && recoveryItemUse.getAsBoolean();
    }

    /**
     * 화면을 떠날 때 열린 퀴즈만 정리한다. 몬스터 HP 등 GameState는 건드리지 않으므로
     * 전투 중단·후퇴 뒤에도 그대로 유지된다. 단, 미니게임으로 나갈 때({@link #withMinigameRevive} 연결 시)는 정리하지 않는다. 재시작·스테이지 이동은 호출하는 쪽(게임 로직)이 한다.
     */
    @Override
    public void battleLeft(QuizBattleController.ExitReason reason) {
        if (reason == QuizBattleController.ExitReason.MINIGAME && reviveAfterMinigame != null) {
            return; // 미니게임 성공 뒤 같은 문제·같은 몬스터로 돌아와야 하므로 열린 퀴즈와 대상을 지우지 않는다
        }
        cancelQuiz.run();
    }
}
