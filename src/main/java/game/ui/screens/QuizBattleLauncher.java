package game.ui.screens;

import game.common.ScreenType;
import game.ui.ScreenManager;

/**
 * 게임 화면에서 몬스터 퀴즈 전투를 열고, 끝나면 원래 화면으로 돌아가게 하는 연결점.
 *
 * 하는 일:
 * 1. 지금 화면(예: 게임 맵)을 기억하고 QUIZ 화면으로 전환한다.
 * 2. {@link QuizBattleController}로 전투를 진행한다. 판정·HP·몬스터 상태는 넘겨받은 BattleRules(GameService)가 맡는다.
 * 3. 플레이어가 화면을 떠나면:
 *    - 승리·문제 없음·전투 중단·체력 보충: 먼저 원래 화면으로 돌아간 뒤 handler에 이유를 알린다.
 *    - 스테이지 재시작·스테이지 선택: 화면을 바꾸지 않고 handler에 알린다 (어디로 갈지는 게임 로직이 정한다).
 *
 * 전제: GameApp에서 {@code ScreenType.QUIZ}로 {@link QuizScreen}이 등록되어 있어야 한다.
 */
public final class QuizBattleLauncher {

    /** 전투 화면을 떠난 이유를 게임 로직에 알린다. */
    @FunctionalInterface
    public interface ExitHandler {
        void onBattleExit(QuizBattleController.ExitReason reason);
    }

    public static final String RETURN_BUTTON_TEXT = "돌아가기";

    private final ScreenManager screenManager;
    private final QuizScreen quizScreen;

    private QuizBattleController active;
    private ScreenType returnTo;
    /** GAME OVER에서 미니게임으로 나가 있는 전투 (성공하면 {@link #resumeAfterMinigame()}으로 이어 간다) */
    private QuizBattleController suspended;
    private ScreenType suspendedReturnTo;

    public QuizBattleLauncher(ScreenManager screenManager) {
        if (screenManager == null) {
            throw new IllegalArgumentException("screenManager is null");
        }
        if (!(screenManager.getScreen(ScreenType.QUIZ) instanceof QuizScreen screen)) {
            throw new IllegalStateException("ScreenType.QUIZ에 QuizScreen이 등록되어 있지 않습니다");
        }
        this.screenManager = screenManager;
        this.quizScreen = screen;
    }

    /**
     * 전투를 시작한다. 이미 진행 중인 전투가 있으면 아무것도 하지 않고 false를 돌려준다
     * (몬스터와 겹친 상태에서 상호작용 키가 여러 번 눌려도 전투는 하나).
     *
     * @param timeLimitSeconds 문제당 제한시간, {@link QuizScreen#NO_TIME_LIMIT}이면 제한 없음
     */
    public boolean startBattle(QuizBattleController.BattleRules rules, String playerName, String monsterName,
                               int timeLimitSeconds, ExitHandler handler) {
        if (rules == null || handler == null) {
            throw new IllegalArgumentException("rules, handler는 null일 수 없습니다");
        }
        if (active != null) {
            return false;
        }
        ScreenType current = screenManager.getCurrentType();
        suspended = null; // 새 전투를 시작하면 미니게임 대기 중이던 전투는 버린다
        returnTo = current == ScreenType.QUIZ ? null : current;
        QuizBattleController controller = new QuizBattleController(quizScreen, rules, timeLimitSeconds,
                RETURN_BUTTON_TEXT, reason -> finish(reason, handler));
        active = controller;
        screenManager.show(ScreenType.QUIZ);
        controller.start(playerName, monsterName);
        return true;
    }

    public boolean isBattleActive() {
        return active != null;
    }

    /** 미니게임에 나가 있어 성공 소식을 기다리는 전투가 있는지 */
    public boolean isWaitingForMinigame() {
        return suspended != null;
    }

    /**
     * 미니게임에 성공했을 때 호출한다. 퀴즈 화면으로 돌아와 플레이어를 되살리고(GameService.reviveAfterMinigame, HP 2)
     * 죽기 직전의 같은 문제를 새 제한시간으로 다시 낸다. 몬스터의 남은 HP는 그대로다. 되살리지 못하면 false를 돌려주고
     * 화면은 바꾸지 않는다. 미니게임 실패·포기 처리는 이 메서드가 아니라 게임 로직이 정한다.
     */
    public boolean resumeAfterMinigame() {
        if (suspended == null) {
            return false;
        }
        QuizBattleController controller = suspended;
        ScreenType back = suspendedReturnTo;
        if (!controller.resumeAfterMinigame()) {
            return false; // 되살리지 못했다: 화면과 대기 상태는 그대로 둔다
        }
        suspended = null;
        suspendedReturnTo = null;
        active = controller;
        returnTo = back;
        screenManager.show(ScreenType.QUIZ);
        return true;
    }

    /** 진행 중인 전투의 컨트롤러 (없으면 null). 테스트·디버그용 */
    public QuizBattleController getActiveController() {
        return active;
    }

    private void finish(QuizBattleController.ExitReason reason, ExitHandler handler) {
        if (reason == QuizBattleController.ExitReason.MINIGAME) {
            suspended = active;
            suspendedReturnTo = returnTo;
        }
        active = null;
        ScreenType target = returnTo;
        returnTo = null;
        if (target != null && returnsToPreviousScreen(reason)) {
            screenManager.show(target);
        }
        handler.onBattleExit(reason);
    }

    private static boolean returnsToPreviousScreen(QuizBattleController.ExitReason reason) {
        return switch (reason) {
            case VICTORY, NO_QUIZ, ABORTED, GO_RECOVER -> true;
            case RESTART_STAGE, STAGE_SELECT, MINIGAME, MAIN_MENU -> false;
        };
    }
}
