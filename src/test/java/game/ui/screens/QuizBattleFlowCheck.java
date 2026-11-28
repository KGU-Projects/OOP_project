package game.ui.screens;

import game.common.ScreenType;
import game.model.GameState;
import game.model.ItemType;
import game.model.Stage;
import game.model.quiz.ChoiceQuiz;
import game.model.quiz.Quiz;
import game.repository.file.FileQuizRepository;
import game.repository.file.FileStageRepository;
import game.ui.ScreenManager;
import game.ui.components.SketchHearts;
import game.ui.components.SketchLabel;
import game.ui.components.SketchRichText;

import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 퀴즈 전투 흐름 점검: PR #33 GameService의 규칙(적 HP, 문제 선택, 회복 질문, 사망 시 문제 유지, 미니게임 성공)을 따르는
 * 시험용 엔진을 {@link GameServiceBattleRules}로 연결해, 컨트롤러와 화면이 그 규칙대로 보여 주는지 확인한다.
 * GameService 자체는 쓰지 않으므로 PR #33이 병합되기 전에도 컴파일·실행된다 (실제 엔진과의 통합은 별도 점검).
 *
 * 확인: 적 HP 3→2→1→0과 승리 화면의 빈 하트, 다음 문제 중복 없음, 결과 문구(HP 감소 설명 없음, 시간 초과 제목 중복 없음),
 * 사망 시 정답·해설 비공개와 GAME OVER, 미니게임 성공 뒤 같은 문제·HP 2·새 타이머, 회복 질문 두 선택, 전투 중단 뒤 HP 유지,
 * 시간 초과는 엔진 timeOut 1회.
 * PASS / FAIL을 구분하고, 화면이 없는 환경이면 SKIP을 출력한다(통과로 세지 않음).
 */
public final class QuizBattleFlowCheck {

    private static int pass;
    private static int fail;
    private static JFrame frame;
    private static Container root;
    private static QuizScreen screen;
    private static QuizBattleLauncher launcher;
    private static ScreenManager sm;

    record Result(boolean correct, int damageTaken, int enemyDamage, boolean dead, boolean defeated) {
    }

    /** PR #33 GameService의 전투 규칙을 흉내 낸 시험용 엔진 */
    static final class Engine {
        final GameState state;
        final List<Quiz> pool;
        final Set<Integer> used = new HashSet<>();
        Quiz current;
        boolean hasTarget;
        int enemyHp;
        int enemyMax = 3;
        boolean asking;
        int submitCalls;
        int timeOutCalls;
        int cancelCalls;
        int reviveCalls;
        int idx;

        Engine(GameState state, List<Quiz> pool) {
            this.state = state;
            this.pool = pool;
        }

        Optional<Quiz> open() {
            enemyHp = enemyHp > 0 ? enemyHp : enemyMax;
            hasTarget = true;
            current = pick();
            return Optional.of(current);
        }

        Quiz pick() {
            for (int i = 0; i < pool.size(); i++) {
                Quiz q = pool.get((idx + i) % pool.size());
                if (!used.contains(q.getId())) {
                    idx = (idx + i + 1) % pool.size();
                    used.add(q.getId());
                    return q;
                }
            }
            used.clear();
            return pick();
        }

        Result submit(String answer) {
            submitCalls++;
            asking = false;
            if (current.isCorrect(answer)) {
                enemyHp--;
                if (enemyHp > 0) {
                    current = pick();
                    return new Result(true, 0, 1, false, false);
                }
                current = null;
                hasTarget = false; // 처치: 엔진이 대상을 지운다 (이후 getEnemyMaxHp는 0)
                return new Result(true, 0, 1, false, true);
            }
            return wrong();
        }

        Result timeOut() {
            timeOutCalls++;
            asking = false;
            return wrong();
        }

        private Result wrong() {
            state.damage(1);
            boolean dead = state.isDead();
            if (!dead) {
                current = pick();
            }
            if (state.getHp() == 1 && state.getItemCount(ItemType.HEALTH_POTION) > 0) {
                asking = true;
            }
            return new Result(false, 1, 0, dead, false);
        }

        boolean answer(boolean use) {
            asking = false;
            return use && ItemType.HEALTH_POTION.use(state);
        }

        boolean revive() {
            reviveCalls++;
            return state.isDead() && state.heal(2);
        }

        int enemyHp() {
            return hasTarget ? enemyHp : 0;
        }

        int enemyMax() {
            return hasTarget ? enemyMax : 0;
        }

        void cancel() {
            cancelCalls++;
            current = null;
            hasTarget = false;
            asking = false;
        }
    }

    private static void check(boolean ok, String message) {
        System.out.println((ok ? "PASS " : "FAIL ") + message);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void sleep(int ms) throws Exception {
        Thread.sleep(ms);
        edt(() -> { });
    }

    private static void waitIdle() throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(30);
            boolean[] busy = new boolean[1];
            edt(() -> busy[0] = screen.isFeedbackActive() || screen.isEffectRunning());
            if (!busy[0]) {
                break;
            }
        }
        sleep(60);
    }

    private static Component find(Container c, String name) {
        for (Component k : c.getComponents()) {
            if (name.equals(k.getName())) {
                return k;
            }
            if (k instanceof Container cc) {
                Component r = find(cc, name);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void click(String name) throws Exception {
        edt(() -> {
            Component c = find(root, name);
            if (c == null || !c.isShowing()) {
                throw new IllegalStateException("보이지 않음: " + name);
            }
            ((AbstractButton) c).doClick(0);
        });
        waitIdle();
    }

    private static boolean showing(String name) {
        Component c = find(root, name);
        return c != null && c.isShowing();
    }

    private static String label(String name) {
        return ((SketchLabel) find(root, name)).getText();
    }

    private static SketchHearts hearts(String name) {
        return (SketchHearts) find(root, name);
    }

    private static String plain() {
        return ((SketchRichText) find(root, "questionArea")).getPlainText();
    }

    private static int correctNo() {
        return ((ChoiceQuiz) screen.getCurrentQuiz()).getAnswerNo();
    }

    private static String right() {
        return "choice" + correctNo();
    }

    private static String wrongChoice() {
        return "choice" + (correctNo() == 1 ? 2 : 1);
    }

    private static boolean noHpText() {
        String all = plain() + label("resultTitle") + (showing("resultDetail") ? label("resultDetail") : "");
        return !all.contains("HP -") && !all.contains("몬스터 HP") && !all.contains("사용자 HP") && !all.contains("플레이어 HP");
    }

    private static final List<QuizBattleController.ExitReason> exits = new ArrayList<>();

    private static GameServiceBattleRules<Result> rules(Engine e) {
        return new GameServiceBattleRules<Result>(() -> e.state, e::open, e::submit,
                r -> new QuizBattleController.Judgement(r.correct(), r.damageTaken(), r.enemyDamage(), r.dead(), r.defeated()), e::cancel)
                .withEnemyHp(e::enemyHp, e::enemyMax)
                .withRecoveryPrompt(() -> e.asking, e::answer)
                .withMinigameRevive(e::revive)
                .withTimeOut(e::timeOut);
    }

    private static void startBattle(Engine e, int limit) throws Exception {
        exits.clear();
        Optional<Quiz> opened = e.open();
        boolean[] first = {true};
        GameServiceBattleRules<Result> r = new GameServiceBattleRules<Result>(() -> e.state,
                () -> first[0] ? (first[0] = false) ? opened : Optional.ofNullable(e.current) : Optional.ofNullable(e.current),
                e::submit, x -> new QuizBattleController.Judgement(x.correct(), x.damageTaken(), x.enemyDamage(), x.dead(), x.defeated()), e::cancel)
                .withEnemyHp(e::enemyHp, e::enemyMax)
                .withRecoveryPrompt(() -> e.asking, e::answer)
                .withMinigameRevive(e::revive)
                .withTimeOut(e::timeOut);
        edt(() -> launcher.startBattle(r, "닉네임", "MONSTER", limit, exits::add));
        sleep(300);
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: 화면이 없는 환경이라 실행하지 않았습니다 (통과로 세지 않음).");
            return;
        }
        List<Quiz> pool = new ArrayList<>();
        for (Quiz q : new FileQuizRepository(Path.of(args.length > 0 ? args[0] : "data/quiz.txt")).findAll()) {
            if (q instanceof ChoiceQuiz) {
                pool.add(q);
            }
        }
        Stage stage = new FileStageRepository(Path.of("data/stages")).findByNumber(2).orElseThrow();

        edt(() -> {
            frame = new JFrame("quiz-flow");
            CardLayout cl = new CardLayout();
            JPanel c = new JPanel(cl);
            c.setPreferredSize(new Dimension(1280, 720));
            sm = new ScreenManager(c, cl);
            screen = new QuizScreen(sm);
            sm.register(ScreenType.QUIZ, screen);
            sm.register(ScreenType.GAME, new StartScreen(sm)); // 전투 전에 있던 화면(맵 대신)
            frame.add(c);
            frame.pack();
            frame.setLocation(0, 0);
            frame.setVisible(true);
            sm.show(ScreenType.GAME);
            root = frame.getContentPane();
            launcher = new QuizBattleLauncher(sm);
        });
        sleep(300);

        // ===== 1. 적 HP 3 → 2 → 1 → 0, 문제 중복 없음, 결과 문구, 승리 화면
        Engine e = new Engine(new GameState(stage, 3, null), pool);
        startBattle(e, 20);
        check(hearts("monsterHearts").getHp() == 3 && hearts("monsterHearts").getMaxHp() == 3 && hearts("playerHearts").getHp() == 3, "시작: 플레이어 3/3, 몬스터 3/3 (엔진 값)");
        check(label("playerName").equals("닉네임") && label("monsterName").equals("골렘"), "이름 표시: 닉네임 / 골렘");
        Set<Integer> ids = new HashSet<>();
        ids.add(screen.getCurrentQuiz().getId());
        click(right());
        check(e.enemyHp() == 2 && hearts("monsterHearts").getHp() == 2 && screen.getPhase() == QuizScreen.Phase.SHOWING_RESULT && showing("nextButton") && !showing("returnButton"),
                "정답 1 → 몬스터 HP 2, 전투 계속(다음 문제 버튼)");
        check(label("resultTitle").equals("정답입니다!") && label("resultDetail").equals("반격 성공!") && noHpText(), "정답 결과: 제목 '정답입니다!', 보조 '반격 성공!', HP 감소 문구 없음");
        click("nextButton");
        check(ids.add(screen.getCurrentQuiz().getId()), "다음 문제는 이전과 다른 문제 (#" + screen.getCurrentQuiz().getId() + ")");
        click(wrongChoice());
        check(e.state.getHp() == 2 && hearts("playerHearts").getHp() == 2 && label("resultTitle").equals("오답입니다!") && label("resultDetail").equals("방어 실패!") && noHpText(),
                "오답 → 플레이어 HP 2, 결과 '오답입니다!' + '방어 실패!'");
        click("nextButton");
        check(ids.add(screen.getCurrentQuiz().getId()), "오답 뒤에도 다른 문제 (#" + screen.getCurrentQuiz().getId() + ")");
        click(right());
        check(e.enemyHp() == 1 && hearts("monsterHearts").getHp() == 1 && !showing("returnButton"), "정답 2 → 몬스터 HP 1, 아직 승리 아님");
        click("nextButton");
        click(right());
        check(e.enemyMax() == 0, "처치 뒤 엔진의 적 최대 HP 조회값은 0 (PR #33 동작)");
        check(hearts("monsterHearts").getMaxHp() == 3 && hearts("monsterHearts").getHp() == 0, "그래도 승리 화면의 몬스터 하트는 3칸 모두 빈 하트 (" + hearts("monsterHearts").getHp() + "/" + hearts("monsterHearts").getMaxHp() + ")");
        check(screen.getPhase() == QuizScreen.Phase.FINISHED && showing("returnButton") && plain().contains("승리! 몬스터를 쓰러뜨렸습니다.") && plain().contains("정답 :") && noHpText(),
                "승리 화면: 돌아가기 버튼, 승리 문구, 정답·해설");
        click("returnButton");
        check(exits.equals(List.of(QuizBattleController.ExitReason.VICTORY)), "복귀 → VICTORY 한 번: " + exits);

        // ===== 2. 다음 전투는 이전 몬스터 HP를 물려받지 않는다
        Engine e2 = new Engine(new GameState(stage, 3, null), pool);
        e2.enemyMax = 2;
        startBattle(e2, 20);
        check(hearts("monsterHearts").getMaxHp() == 2 && hearts("monsterHearts").getHp() == 2, "새 전투: 최대 HP 2로 초기화 (이전 3이 남지 않음)");

        // ===== 3. 전투 중단: 엔진 HP 유지, 재진입 시 표시
        click(right());
        edt(() -> screen.openPause());
        sleep(150);
        click("popupSecondary");
        check(exits.equals(List.of(QuizBattleController.ExitReason.ABORTED)) && e2.cancelCalls == 1, "전투 중단 → ABORTED, 열린 퀴즈만 정리");
        e2.hasTarget = false;
        startBattle(e2, 20);
        check(e2.enemyHp() == 1 && hearts("monsterHearts").getHp() == 1 && hearts("monsterHearts").getMaxHp() == 2, "재진입: 실제 남은 몬스터 HP 1/2 표시 (3으로 초기화하지 않음)");

        edt(() -> screen.openPause());
        sleep(150);
        click("popupSecondary");
        e2.hasTarget = false;

        // ===== 4. 시간 초과: 엔진 timeOut 1회, 제목 중복 없음
        startBattle(e2, 1);
        check(waitFor(() -> screen.getPhase() == QuizScreen.Phase.SHOWING_RESULT, 4000), "1초 제한시간 종료 → 결과 화면");
        waitIdle();
        check(e2.timeOutCalls == 1 && e2.submitCalls == 1 && e2.state.getHp() == 2 && hearts("playerHearts").getHp() == 2, "엔진 timeOut() 1회 호출, HP 3 → 2 한 번만, 하트 2");
        check(label("resultTitle").equals("시간 초과!") && label("resultDetail").equals("방어 실패!") && plain().contains("제출 없음") && noHpText(),
                "시간 초과 결과: 제목 '시간 초과!' + 보조 '방어 실패!' (제목 반복 없음)");
        edt(() -> screen.openPause());
        sleep(150);
        click("popupSecondary");

        // ===== 5. 사망: 정답·해설 비공개, GAME OVER, 미니게임 성공 후 같은 문제·HP 2·새 타이머
        Engine e3 = new Engine(new GameState(stage, 3, null), pool);
        startBattle(e3, 20);
        click(right());
        click("nextButton");
        e3.state.damage(1); // HP 2
        // 실제 오답으로 HP 1 → 0
        click(wrongChoice());
        check(e3.state.getHp() == 1 && screen.getPopup() == null && !e3.asking, "포션이 없으면 HP 1이어도 팝업 없음 (엔진이 묻지 않음)");
        click("nextButton");
        int deadId = screen.getCurrentQuiz().getId();
        int enemyBefore = e3.enemyHp();
        click(wrongChoice());
        check(e3.state.isDead() && screen.getPopup() == QuizScreen.PopupType.GAME_OVER && hearts("playerHearts").getHp() == 0, "HP 0 → 플레이어 하트 0, GAME OVER 팝업");
        check(!showing("resultTitle") && !showing("nextButton") && !plain().contains("정답 :") && !plain().contains("해설"), "정답·해설·정답 번호를 보여 주는 결과 화면이 없다");
        check(!showing("timerLabel"), "GAME OVER에서 타이머 숨김");
        click("popupPrimary"); // 미니게임
        check(exits.equals(List.of(QuizBattleController.ExitReason.MINIGAME)) && e3.cancelCalls == 0 && e3.current != null && e3.current.getId() == deadId,
                "미니게임 이동 → 현재 문제 #" + deadId + " 와 대상이 지워지지 않는다 (cancel " + e3.cancelCalls + "회)");
        check(launcher.isWaitingForMinigame() && !launcher.isBattleActive(), "런처: 미니게임 대기");
        boolean[] ok = new boolean[1];
        edt(() -> ok[0] = launcher.resumeAfterMinigame());
        sleep(400);
        check(ok[0] && e3.reviveCalls == 1 && e3.state.getHp() == 2 && hearts("playerHearts").getHp() == 2, "미니게임 성공 → 엔진 reviveAfterMinigame, HP 2");
        check(screen.getCurrentQuiz().getId() == deadId && e3.enemyHp() == enemyBefore && hearts("monsterHearts").getHp() == enemyBefore, "같은 문제·같은 몬스터 HP (" + enemyBefore + ")");
        check(screen.getPhase() == QuizScreen.Phase.ANSWERING && screen.getPopup() == null && !showing("resultTitle") && screen.getRemainingSeconds() >= 19,
                "정답 미공개 상태로 문제 표시, 새 20초 타이머 (TIME " + screen.getRemainingSeconds() + ")");
        click(right());
        check(e3.enemyHp() == enemyBefore - 1 && e3.submitCalls == 4, "복귀한 문제의 정답은 한 번만 판정되어 몬스터 HP " + (enemyBefore - 1));
        click("nextButton");
        edt(() -> screen.openPause());
        sleep(150);
        click("popupSecondary");

        // ===== 6. 회복 질문: 엔진이 물을 때만, 두 선택 모두 질문 상태 해제
        Engine e4 = new Engine(new GameState(stage, 3, null), pool);
        e4.state.addItem(ItemType.HEALTH_POTION, 1);
        e4.state.damage(1); // HP 2 (시작할 때 HP 1이 아니므로 팝업 없음)
        startBattle(e4, 20);
        check(screen.getPopup() == null, "전투 시작 시에는 엔진이 묻지 않으므로 팝업 없음");
        click(wrongChoice());
        check(e4.state.getHp() == 1 && e4.asking && screen.getPopup() == QuizScreen.PopupType.LOW_HP, "HP 1 + 포션 → 엔진이 묻고 팝업이 뜬다");
        click("popupPrimary");
        check(e4.state.getHp() == 2 && e4.state.getItemCount(ItemType.HEALTH_POTION) == 0 && !e4.asking && hearts("playerHearts").getHp() == 2,
                "회복 아이템 사용 → HP 2, 포션 0, 질문 해제");
        click("nextButton");
        e4.state.addItem(ItemType.HEALTH_POTION, 1);
        click(wrongChoice());
        check(screen.getPopup() == QuizScreen.PopupType.LOW_HP && e4.asking, "다시 HP 1 + 포션 → 팝업(전투당 1회 제한 없이 엔진이 물을 때마다)");
        click("popupSecondary");
        check(e4.state.getHp() == 1 && e4.state.getItemCount(ItemType.HEALTH_POTION) == 1 && !e4.asking && screen.getPopup() == null,
                "계속 싸우기 → HP·포션 유지, 질문 해제");

        edt(() -> frame.dispose());
        System.out.println("결과: PASS " + pass + " / FAIL " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    private static boolean waitFor(java.util.function.BooleanSupplier cond, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(40);
        }
        return cond.getAsBoolean();
    }
}
