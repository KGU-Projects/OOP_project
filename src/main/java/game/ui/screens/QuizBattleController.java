package game.ui.screens;

import game.model.quiz.Quiz;

import java.util.Optional;

/**
 * 몬스터 퀴즈 전투에서 {@link QuizScreen}의 입력 이벤트와 게임 로직을 연결한다.
 *
 * 역할 분리:
 * - QuizScreen: 화면 표시와 입력 (판정·HP 계산을 하지 않는다)
 * - BattleRules(실제 게임에서는 GameService): 정답 판정, HP·점수 변경, 승패 결정. 상태는 GameState에 있다
 * - 이 클래스: 화면 이벤트를 규칙 호출로 바꾸고, 규칙이 돌려준 결과와 현재 HP를 화면에 그대로 보여 준다.
 *   HP를 직접 계산하거나 바꾸지 않으므로 HP가 두 번 바뀌지 않는다.
 *
 * 흐름: start → (제출 또는 시간 초과) → rules 판정 1회 → 결과 표시 → '다음 문제' → rules.nextQuiz → …
 * - 플레이어 HP가 1이 되면 전투당 한 번 경고 팝업 (계속 싸우기 / 체력 보충하러 가기)
 * - 일시정지 팝업 (계속하기 / 전투 중단): 화면이 타이머를 멈추고 다시 이어 간다
 * - 승리·문제 없음 → 복귀 버튼, HP 0 → GAME OVER 팝업 (현재 스테이지 재시작 / 스테이지 선택으로)
 * 화면을 떠나는 선택은 {@link BattleCallbacks#onExit}로 전투당 한 번만 알린다.
 *
 * 화면 전환(ScreenManager.show), 스테이지 재시작, 몬스터 HP·진행 유지는 이 클래스가 하지 않는다.
 * 호출하는 쪽이 QUIZ 화면을 띄운 뒤 start를 부르고, onExit에서 이유에 맞게 처리한다.
 */
public final class QuizBattleController implements QuizScreen.QuizListener {

    /** 전투가 끝난 상태 */
    public enum Outcome {
        /** 몬스터를 쓰러뜨림 */
        VICTORY,
        /** 플레이어 HP가 0이 됨 */
        DEFEAT,
        /** 낼 문제가 없어 전투를 진행할 수 없음 (데이터 문제) */
        NO_QUIZ,
        /** 일시정지에서 전투 중단 */
        ABORTED,
        /** HP 경고에서 체력 보충하러 감 */
        RETREATED
    }

    /** 플레이어가 전투 화면을 떠나며 고른 것. 게임 로직은 이 값에 따라 화면 이동·재시작을 한다. */
    public enum ExitReason {
        /** 승리 후 복귀 버튼 */
        VICTORY,
        /** 문제가 없어 끝난 뒤 복귀 버튼 */
        NO_QUIZ,
        /** 일시정지 → 전투 중단. 몬스터 HP·진행 상태 유지는 게임 로직 책임 */
        ABORTED,
        /** HP 경고 → 체력 보충하러 가기 */
        GO_RECOVER,
        /** GAME OVER → 현재 스테이지 재시작 */
        RESTART_STAGE,
        /** GAME OVER → 스테이지 선택으로 돌아가기 */
        STAGE_SELECT
    }

    /** 전투 화면 선택을 게임 로직에 알린다. 람다로 onExit만 넘겨도 된다. */
    @FunctionalInterface
    public interface BattleCallbacks {
        /** 화면을 떠나는 선택. 전투당 정확히 한 번 호출된다. */
        void onExit(ExitReason reason);

        /** 전투를 이어 가는 선택 (LOW_HP_CONTINUE, PAUSE_RESUME). 기록·사운드 등이 필요할 때만 구현한다. */
        default void onContinue(QuizScreen.PopupChoice choice) {
        }
    }

    /** 이 HP가 되면 경고 팝업을 띄운다 (전투당 한 번) */
    public static final int LOW_HP_WARNING_AT = 1;

    /**
     * 한 번의 판정 결과. 값은 모두 게임 로직(BattleRules)이 정한 것이고 이 클래스는 표시만 한다.
     * 실제 게임에서는 GameService의 AnswerResult를 그대로 옮겨 담는다.
     *
     * @param damageTaken     이번 판정으로 플레이어가 잃은 HP
     * @param monsterDamage   이번 판정으로 몬스터가 잃은 HP
     * @param playerDead      플레이어 HP가 0이 되었는지
     * @param monsterDefeated 몬스터가 쓰러졌는지
     */
    public record Judgement(boolean correct, int damageTaken, int monsterDamage,
                            boolean playerDead, boolean monsterDefeated) {
    }

    /** 화면에 보여 줄 현재 HP. GameState에서 읽은 값을 그대로 담는다. */
    public record HpView(int playerHp, int playerMaxHp, int monsterHp, int monsterMaxHp) {
    }

    /**
     * 전투 규칙. 판정과 상태 변경은 모두 여기서 일어난다.
     * 각 메서드는 문제 하나당 한 번만 호출된다 (QuizScreen과 이 클래스가 중복 호출을 막는다).
     */
    public interface BattleRules {
        /** 다음에 낼 문제. 없으면 empty (전투를 NO_QUIZ로 끝낸다). */
        Optional<Quiz> nextQuiz();

        /** 답 제출을 판정하고 상태를 바꾼다. */
        Judgement submitAnswer(Quiz quiz, String answer);

        /**
         * 제한시간이 지났을 때의 판정. 제한시간을 쓰는 경우(timeLimitSeconds > 0)에만 호출된다.
         * 시간 초과 규칙이 없는 게임은 제한시간을 {@link QuizScreen#NO_TIME_LIMIT}으로 두면 된다.
         */
        default Judgement timeOut(Quiz quiz) {
            throw new UnsupportedOperationException("시간 초과 규칙이 없습니다. 제한시간을 NO_TIME_LIMIT으로 두세요.");
        }

        /** 지금 상태의 HP (판정 직후 다시 읽어 화면에 반영한다). */
        HpView hp();

        /**
         * 플레이어가 전투 화면을 떠나기 직전에 한 번 호출된다 (onExit보다 먼저).
         * 실제 게임에서는 여기서 열린 퀴즈를 정리하고(GameService.cancelQuiz 등) 몬스터 HP·진행 상태를 유지한다.
         */
        default void battleLeft(ExitReason reason) {
        }
    }

    private final QuizScreen screen;
    private final BattleRules rules;
    private final int timeLimitSeconds;
    private final String returnButtonText;
    private final BattleCallbacks callbacks;

    private Quiz currentQuiz;
    private int questionCount;
    private boolean awaitingJudgement;
    private Outcome outcome;
    private boolean finishReported;
    private boolean running;
    private boolean lowHpWarned;
    private boolean lowHpPopupOpen;

    /**
     * @param timeLimitSeconds 문제당 제한시간. {@link QuizScreen#NO_TIME_LIMIT}이면 시간 제한 없음
     * @param returnButtonText 전투 종료 후 복귀 버튼 문구 (예: "돌아가기", 데모는 "다시 플레이")
     * @param callbacks        화면을 떠나는 선택(ExitReason)을 받는다. 전투당 한 번 호출된다
     */
    public QuizBattleController(QuizScreen screen, BattleRules rules, int timeLimitSeconds,
                                String returnButtonText, BattleCallbacks callbacks) {
        if (screen == null || rules == null || callbacks == null) {
            throw new IllegalArgumentException("screen, rules, callbacks는 null일 수 없습니다");
        }
        if (timeLimitSeconds < 0) {
            throw new IllegalArgumentException("timeLimitSeconds must be 0 (no limit) or positive: " + timeLimitSeconds);
        }
        this.screen = screen;
        this.rules = rules;
        this.timeLimitSeconds = timeLimitSeconds;
        this.returnButtonText = returnButtonText;
        this.callbacks = callbacks;
    }

    /**
     * 전투를 시작한다. 이 컨트롤러를 화면의 리스너로 등록하고 첫 문제를 낸다.
     * 전투가 끝난 뒤 다시 부르면 새 전투를 시작한다. HP는 rules가 가진 현재 값을 그대로 쓴다
     * (같은 스테이지의 전투 사이에 HP를 유지하는지는 게임 로직이 정한다).
     */
    public void start(String playerName, String monsterName) {
        screen.setQuizListener(this);
        currentQuiz = null;
        questionCount = 0;
        awaitingJudgement = false;
        outcome = null;
        finishReported = false;
        running = true;
        lowHpWarned = false;
        lowHpPopupOpen = false;

        screen.resetBattle();
        screen.setCombatantNames(playerName, monsterName);
        HpView hp = refreshHp();
        showNextQuiz();
        maybeWarnLowHp(hp); // 이전 전투에서 HP 1로 들어온 경우
    }

    // ---------------------------------------------------------------------
    // QuizScreen.QuizListener
    // ---------------------------------------------------------------------

    @Override
    public void onAnswerSubmitted(Quiz quiz, String answer) {
        if (!acceptsJudgementFor(quiz)) {
            return;
        }
        awaitingJudgement = false;
        Judgement j = rules.submitAnswer(quiz, answer);
        applyJudgement(j, j.correct() ? QuizScreen.ResultType.CORRECT : QuizScreen.ResultType.WRONG);
    }

    @Override
    public void onTimeExpired(Quiz quiz) {
        if (!acceptsJudgementFor(quiz)) {
            return;
        }
        awaitingJudgement = false;
        applyJudgement(rules.timeOut(quiz), QuizScreen.ResultType.TIMEOUT);
    }

    @Override
    public void onNextRequested() {
        if (!running || outcome != null || awaitingJudgement || currentQuiz == null) {
            return;
        }
        showNextQuiz();
    }

    @Override
    public void onReturnRequested() {
        if (outcome == Outcome.VICTORY) {
            exit(ExitReason.VICTORY);
        } else if (outcome == Outcome.NO_QUIZ) {
            exit(ExitReason.NO_QUIZ);
        }
    }

    @Override
    public void onPopupChoice(QuizScreen.PopupChoice choice) {
        if (choice == null || finishReported) {
            return;
        }
        switch (choice) {
            case LOW_HP_CONTINUE -> {
                if (lowHpPopupOpen) {
                    lowHpPopupOpen = false;
                    callbacks.onContinue(choice);
                }
            }
            case LOW_HP_RECOVER -> {
                if (lowHpPopupOpen) {
                    lowHpPopupOpen = false;
                    outcome = Outcome.RETREATED;
                    exit(ExitReason.GO_RECOVER);
                }
            }
            case PAUSE_RESUME -> {
                if (running && outcome == null) {
                    callbacks.onContinue(choice);
                }
            }
            case PAUSE_ABORT -> {
                if (running && outcome == null) {
                    outcome = Outcome.ABORTED;
                    exit(ExitReason.ABORTED);
                }
            }
            case GAME_OVER_RESTART -> {
                if (outcome == Outcome.DEFEAT) {
                    exit(ExitReason.RESTART_STAGE);
                }
            }
            case GAME_OVER_STAGE_SELECT -> {
                if (outcome == Outcome.DEFEAT) {
                    exit(ExitReason.STAGE_SELECT);
                }
            }
        }
    }

    // ---------------------------------------------------------------------

    public Quiz getCurrentQuiz() {
        return currentQuiz;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    /** 전투가 끝났으면 상태, 진행 중이면 null */
    public Outcome getOutcome() {
        return outcome;
    }

    // ---------------------------------------------------------------------

    private boolean acceptsJudgementFor(Quiz quiz) {
        return running && outcome == null && awaitingJudgement && quiz != null && quiz == currentQuiz;
    }

    /** 화면을 떠나는 선택: rules → callbacks 순서로 전투당 한 번만 알린다. */
    private void exit(ExitReason reason) {
        if (finishReported) {
            return;
        }
        finishReported = true;
        running = false;
        awaitingJudgement = false;
        rules.battleLeft(reason);
        callbacks.onExit(reason);
    }

    private void applyJudgement(Judgement j, QuizScreen.ResultType type) {
        if (j == null) {
            throw new IllegalStateException("BattleRules가 판정 결과를 돌려주지 않았습니다");
        }
        HpView hp = refreshHp();

        if (j.monsterDefeated()) {
            outcome = Outcome.VICTORY;
        } else if (j.playerDead()) {
            outcome = Outcome.DEFEAT;
        }

        screen.showResult(type, describe(j, type), outcome == null);
        if (outcome == Outcome.VICTORY) {
            screen.showBattleEnd(true, "몬스터를 쓰러뜨렸습니다.", returnButtonText);
        } else if (outcome == Outcome.DEFEAT) {
            screen.showGameOver("체력이 모두 소진되었습니다.");
        } else {
            maybeWarnLowHp(hp);
        }
    }

    /** 전투 중 플레이어 HP가 1이면 전투당 한 번 경고한다. HP 값은 rules가 준 것만 본다. */
    private void maybeWarnLowHp(HpView hp) {
        if (!running || outcome != null || lowHpWarned || hp.playerHp() != LOW_HP_WARNING_AT) {
            return;
        }
        screen.showLowHpWarning("체력이 " + hp.playerHp() + " 남았습니다. 한 번 더 틀리면 GAME OVER입니다.");
        if (screen.getPopup() == QuizScreen.PopupType.LOW_HP) {
            lowHpWarned = true;
            lowHpPopupOpen = true;
        }
    }

    private void showNextQuiz() {
        Optional<Quiz> next = rules.nextQuiz();
        if (next.isEmpty()) {
            outcome = Outcome.NO_QUIZ;
            screen.showBattleEnd(false, "낼 문제가 없습니다.", returnButtonText);
            return;
        }
        currentQuiz = next.get();
        questionCount++;
        awaitingJudgement = true;
        screen.showQuiz(currentQuiz, questionCount, timeLimitSeconds);
    }

    private HpView refreshHp() {
        HpView hp = rules.hp();
        screen.updateHp(hp.playerHp(), hp.playerMaxHp(), hp.monsterHp(), hp.monsterMaxHp());
        return hp;
    }

    /** 결과 문구. 숫자는 판정 결과에 담긴 값만 쓴다. */
    private static String describe(Judgement j, QuizScreen.ResultType type) {
        StringBuilder sb = new StringBuilder();
        if (j.monsterDamage() > 0) {
            sb.append("반격 성공! 몬스터 HP -").append(j.monsterDamage());
        }
        if (j.damageTaken() > 0) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(type == QuizScreen.ResultType.TIMEOUT ? "시간 초과! " : "방어 실패! ")
                    .append("사용자 HP -").append(j.damageTaken());
        }
        return sb.toString();
    }
}
