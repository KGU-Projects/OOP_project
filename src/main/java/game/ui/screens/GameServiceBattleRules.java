package game.ui.screens;

import game.model.GameState;
import game.model.quiz.Quiz;

import java.util.Optional;
import java.util.function.Function;
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
     * 답 없이 제한시간이 끝난 경우. GameService에는 시간 초과 API가 없으므로 빈 답을 제출해
     * GameService의 오답 규칙을 그대로 따른다. (시간 초과 전용 규칙이 생기면 여기만 바꾸면 된다.)
     * 제한시간을 쓰지 않으려면 컨트롤러에 {@link QuizScreen#NO_TIME_LIMIT}을 준다.
     */
    @Override
    public QuizBattleController.Judgement timeOut(Quiz quiz) {
        return toJudgement.apply(submitAnswer.apply(""));
    }

    /** 플레이어 HP는 GameState 값, 몬스터 HP는 GameState의 보스 값 (GameService에 몬스터 상태가 생기면 교체). */
    @Override
    public QuizBattleController.HpView hp() {
        GameState s = state.get();
        return new QuizBattleController.HpView(s.getHp(), s.getMaxHp(), s.getBossHp(), s.getBossMaxHp());
    }

    /**
     * 화면을 떠날 때 열린 퀴즈만 정리한다. 몬스터 HP 등 GameState는 건드리지 않으므로
     * 전투 중단·후퇴 뒤에도 그대로 유지된다. 재시작·스테이지 이동은 호출하는 쪽(게임 로직)이 한다.
     */
    @Override
    public void battleLeft(QuizBattleController.ExitReason reason) {
        cancelQuiz.run();
    }
}
