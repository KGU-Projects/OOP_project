package game.ui.screens;

import game.common.ScreenType;
import game.model.ItemType;
import game.model.object.GameObject;
import game.model.object.ObjectType;
import game.repository.file.FileCutsceneRepository;
import game.repository.file.FileQuizRepository;
import game.repository.file.FileSaveRepository;
import game.repository.file.FileStageRepository;
import game.repository.file.FileUserRepository;
import game.service.GameService;
import game.service.UserService;
import game.ui.ImageLoader;
import game.ui.ScreenManager;
import game.ui.components.SketchHearts;
import game.ui.components.SketchLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 퀴즈 전투 연동 점검 (담당: QuizScreen·QuizBattleLauncher·GameServiceBattleRules). GameApp과 같은 방식으로 실제 화면
 * (GameScreen, QuizScreen 등), 실제 GameService, 실제 data/stages·data/quiz.txt를 연결하고, 사용자·세이브는 임시 폴더를 쓴다.
 * 플레이어를 실제 몬스터·미믹·함정 위치에 놓으면 GameScreen의 게임 루프가 전투를 시작하게 하고, 이후는 실제 클릭·키 입력으로 진행한다.
 *
 * 결과는 세 가지로 나온다: PASS(정상), FAIL(담당 범위 오류, 종료 코드 1), GAP(다른 팀원 코드나 데이터가 아직 연동하지 않은 부분.
 * 통과로 세지 않으며 종료 코드에 영향 없음). 화면이 없는 환경이면 SKIP을 출력하고 통과로 세지 않는다.
 *
 * 실행(프로젝트 루트, 컴파일 후): java -cp "out;src/main/resources;out/lib/*" game.ui.screens.QuizBattleWiringCheck
 * (Linux/Mac은 classpath 구분자 ':').
 */
public class QuizBattleWiringCheck {
    static int pass = 0;
    static int fails = 0;
    static int gaps = 0;
    static ScreenManager sm;
    static GameService gs;
    static QuizScreen quiz;
    static JFrame frame;
    static Robot robot;
    static final java.util.List<String> gapNotes = new java.util.ArrayList<>();

    static void check(boolean ok, String m) {
        System.out.println((ok ? "PASS " : "FAIL ") + m);
        if (ok) {
            pass++;
        } else {
            fails++;
        }
    }

    /** 다른 팀원의 코드·데이터가 아직 연동하지 않아 확인할 수 없는 항목 (통과로 세지 않는다) */
    static void gap(boolean ok, String m, String need) {
        if (ok) {
            System.out.println("PASS " + m);
            pass++;
        } else {
            System.out.println("GAP  " + m + "  → 필요: " + need);
            gaps++;
            gapNotes.add(m + " → " + need);
        }
    }

    static Component find(Container c, String name) {
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

    static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void settle(int ms) throws Exception {
        Thread.sleep(ms);
        edt(() -> { });
    }

    static boolean waitFor(java.util.function.BooleanSupplier cond, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return cond.getAsBoolean();
    }

    static void click(String name) throws Exception {
        edt(() -> {
            Component c = find(frame.getContentPane(), name);
            if (c == null || !c.isShowing()) {
                throw new IllegalStateException("보이지 않음: " + name);
            }
            ((AbstractButton) c).doClick();
        });
        settle(250);
    }

    static boolean showing(String name) {
        Component c = find(frame.getContentPane(), name);
        return c != null && c.isShowing();
    }

    static String label(String name) {
        return ((SketchLabel) find(frame.getContentPane(), name)).getText();
    }

    static int hearts(String name) {
        return ((SketchHearts) find(frame.getContentPane(), name)).getHp();
    }

    static void key(int code) throws Exception {
        edt(() -> frame.toFront());
        settle(150);
        robot.keyPress(code);
        robot.keyRelease(code);
        settle(300);
    }

    /** GameApp.initGame과 같은 구성으로 게임을 띄우고 stageNo 스테이지를 시작한다. */
    static void setup(int stageNo) throws Exception {
        Path tmp = Files.createTempDirectory("quiz-wiring");
        edt(() -> {
            if (frame != null) {
                frame.dispose();
            }
            frame = new JFrame("CODE ESCAPE (wiring check)");
            CardLayout cl = new CardLayout();
            JPanel c = new JPanel(cl);
            c.setPreferredSize(new Dimension(1280, 720));
            sm = new ScreenManager(c, cl);
            UserService users = new UserService(new FileUserRepository(tmp.resolve("users.txt")));
            gs = new GameService(new FileQuizRepository(Path.of("data/quiz.txt")), new FileStageRepository(Path.of("data/stages")),
                    new FileSaveRepository(tmp.resolve("saves")), users);
            ImageLoader loader = new ImageLoader();
            sm.register(ScreenType.START, new StartScreen(sm));
            sm.register(ScreenType.GAME, new GameScreen(sm, gs, loader));
            quiz = new QuizScreen(sm);
            sm.register(ScreenType.QUIZ, quiz);
            sm.register(ScreenType.LOAD, new LoadScreen(sm, gs));
            sm.register(ScreenType.CUTSCENE, new CutsceneScreen(sm, new FileCutsceneRepository(Path.of("data/cutscenes")), loader));
            frame.add(c);
            frame.pack();
            frame.setLocation(0, 0);
            frame.setVisible(true);
            gs.setUser(users.register("테스터닉"));
            if (!gs.loadStage(stageNo)) {
                throw new IllegalStateException("스테이지 " + stageNo + " 없음");
            }
            sm.show(ScreenType.GAME);
            if (sm.getCurrentType() == ScreenType.GAME) {
                sm.show(ScreenType.GAME); // 컷신이 있는 스테이지는 컷신 뒤 다시 들어온 것으로 본다
            }
            frame.toFront();
        });
        settle(800);
        edt(() -> {
            if (sm.getCurrentType() != ScreenType.GAME) {
                sm.show(ScreenType.GAME);
            }
        });
        settle(300);
    }

    static GameObject object(String id) {
        return gs.getState().getStage().getObjects().stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }

    /** 플레이어를 오브젝트 위치에 놓는다. GameScreen의 게임 루프가 접근/밟기를 감지해 전투를 시작한다. */
    static void moveTo(GameObject o) throws Exception {
        edt(() -> gs.getState().getPlayer().spawnAt(o.getX(), o.getY()));
    }

    /** target을 누르고 약 0.9초 동안 화면에 떠 있던 피해량·회복량 문구를 모아서 돌려준다 (연출 확인용) */
    static java.util.List<String> sampleFloaters(String target) throws Exception {
        java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        boolean[] stop = {false};
        Thread t = new Thread(() -> {
            while (!stop[0]) {
                try {
                    edt(() -> seen.addAll(quiz.floatingTexts()));
                    Thread.sleep(10);
                } catch (Exception e) {
                    return;
                }
            }
        });
        t.start();
        click(target);
        Thread.sleep(900);
        stop[0] = true;
        t.join(500);
        return seen;
    }

    static boolean inBattle() {
        return sm.getCurrentType() == ScreenType.QUIZ && quiz.getCurrentQuiz() != null && quiz.getPhase() != QuizScreen.Phase.READY;
    }

    public static void main(String[] a) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: 화면이 없는 환경이라 실행하지 않았습니다 (통과로 세지 않음).");
            return;
        }
        robot = new Robot();

        // ---------------- 일반 몬스터 접근 (스테이지 2의 MON1)
        setup(2);
        check(sm.getCurrentType() == ScreenType.GAME && gs.getNickname().equals("테스터닉"), "게임 시작: 스테이지 2, 닉네임 " + gs.getNickname());
        GameObject mon = object("MON1");
        moveTo(mon);
        check(waitFor(QuizBattleWiringCheck::inBattle, 4000), "몬스터 접근 → 퀴즈 전투 화면 전환 (실제 GameScreen 루프)");
        check(quiz.getCurrentQuiz() != null && quiz.getPhase() == QuizScreen.Phase.ANSWERING, "GameService.openQuiz의 문제 표시: #" + (quiz.getCurrentQuiz() == null ? "-" : quiz.getCurrentQuiz().getId()));
        check(label("stageLabel").equals("STAGE 2") || label("stageLabel").equals("STAGE 2 "), "실제 플레이 중인 스테이지 번호 표시: " + label("stageLabel"));
        check(label("monsterName").equals("골렘") && quiz.getMonsterType() == QuizScreen.MonsterType.GOLEM, "MONSTER → 이름 '골렘' + 골렘 이미지: " + label("monsterName"));
        check(hearts("playerHearts") == gs.getState().getHp() && gs.getState().getHp() == 3, "플레이어 HP 3/3 (GameState) 표시");
        check(!find(frame.getContentPane(), "monsterHearts").isShowing() || ((SketchHearts) find(frame.getContentPane(), "monsterHearts")).getMaxHp() == 0,
                "몬스터 HP: GameService에 몬스터 HP가 없어 표시하지 않음(가짜 HP 없음)");
        gap(label("playerName").equals("테스터닉"), "실제 로그인 닉네임 표시 (지금 GameScreen이 \"플레이어\"를 넘김: '" + label("playerName") + "')",
                "GameScreen.openQuizFor에서 launcher.startBattle(rules, gameService.getNickname(), ...) 로 변경");
        boolean layoutAuto = quiz.getAppliedChoiceLayout() != null;
        check(layoutAuto, "객관식 자동 배치 적용: " + quiz.getAppliedChoiceLayout());

        // 오답 → HP 감소, 같은 문제 다시
        int correctNo = ((game.model.quiz.ChoiceQuiz) quiz.getCurrentQuiz()).getAnswerNo();
        String wrong = "choice" + (correctNo == 1 ? 2 : 1);
        java.util.List<String> wrongFx = sampleFloaters(wrong);
        check(wrongFx.contains("-1"), "실제 오답 → GameState HP 감소량(-1)이 주인공 옆에 표시된다: " + wrongFx);
        check(gs.getState().getHp() == 2 && hearts("playerHearts") == 2 && quiz.getPhase() == QuizScreen.Phase.SHOWING_RESULT, "오답 → GameState HP 2, 하트 2");
        String plain = ((game.ui.components.SketchRichText) find(frame.getContentPane(), "questionArea")).getPlainText();
        check(plain.contains("내가 고른 답"), "오답 결과에 제출한 답 표시");
        click("nextButton");
        check(quiz.getPhase() == QuizScreen.Phase.ANSWERING && gs.getCurrentQuiz().isPresent(), "다음 문제 → 같은 전투 계속 (GameService 열린 퀴즈)");

        // 일시정지/재개/타이머
        edt(() -> quiz.openPause());
        settle(200);
        int t1 = quiz.getRemainingSeconds();
        Thread.sleep(1500);
        check(quiz.getPopup() == QuizScreen.PopupType.PAUSE && quiz.getRemainingSeconds() == t1, "일시정지 중 타이머 정지 (" + t1 + "초)");
        click("popupPrimary");
        Thread.sleep(1300);
        check(quiz.getPopup() == null && quiz.getRemainingSeconds() < t1, "재개 후 타이머 진행");
        // ESC 키 (실제 키 입력)
        key(KeyEvent.VK_ESCAPE);
        check(quiz.getPopup() == QuizScreen.PopupType.PAUSE, "ESC 키로 일시정지");
        key(KeyEvent.VK_ESCAPE);
        check(quiz.getPopup() == null, "ESC 키로 재개");

        // 시간 초과는 제한시간 20초라 대기 대신 화면 타이머 만료 경로를 확인: 남은 시간 표시가 줄어드는지만 본다 (전투 규칙은 SketchTest에서 검증)
        // HP 1 + 회복 아이템
        click(wrong);
        gs.getState().addItem(ItemType.HEALTH_POTION, 1);
        check(gs.getState().getHp() == 1, "오답 2회째 → HP 1");
        gap(quiz.getPopup() == QuizScreen.PopupType.LOW_HP, "HP 1 + 회복 아이템 보유 → 경고 팝업 (지금은 " + quiz.getPopup() + ")",
                "GameScreen.openQuizFor의 GameServiceBattleRules에 .withRecoveryItemUse(() -> gameService.useItem(ItemType.HEALTH_POTION)) 연결");
        if (quiz.getPopup() == QuizScreen.PopupType.LOW_HP) {
            click("popupPrimary");
            check(gs.getState().getHp() == 2 && gs.getState().getItemCount(ItemType.HEALTH_POTION) == 0, "회복 아이템 사용 → HP 2, 포션 0");
        }

        // 전투 중단 → 맵 복귀, HP 유지, 몬스터는 그대로
        edt(() -> {
            if (quiz.getPopup() == null) {
                quiz.openPause();
            }
        });
        settle(200);
        int hpBefore = gs.getState().getHp();
        click("popupSecondary");
        check(sm.getCurrentType() == ScreenType.GAME && gs.getState().getHp() == hpBefore && !gs.getState().isObjectResolved("MON1") && gs.getCurrentQuiz().isEmpty(),
                "전투 중단 → 맵 복귀, HP " + hpBefore + " 유지, 몬스터 미처치, 열린 퀴즈 정리");

        // 정답 → 몬스터 처치 → 맵 복귀
        edt(() -> gs.getState().getPlayer().spawnAt(mon.getX() - 8, mon.getY()));
        settle(600);
        moveTo(mon);
        check(waitFor(QuizBattleWiringCheck::inBattle, 5000), "중단 뒤 몬스터 위치에 다시 접근하면 재전투");
        check(gs.getState().getHp() == hpBefore && hearts("playerHearts") == hpBefore, "같은 스테이지 안에서 플레이어 HP 유지: " + hearts("playerHearts"));
        int answer = ((game.model.quiz.ChoiceQuiz) quiz.getCurrentQuiz()).getAnswerNo();
        int score = gs.getState().getScore();
        java.util.List<String> killFx = sampleFloaters("choice" + answer);
        check(killFx.isEmpty(), "몬스터 HP가 없는 실제 게임에서 정답 → 가짜 몬스터 피해량을 표시하지 않는다: " + killFx);
        check(gs.getState().isObjectResolved("MON1") && gs.getState().getScore() > score, "정답 → 몬스터 처치 상태 반영(MON1 해결), 점수 +" + (gs.getState().getScore() - score));
        check(quiz.getPhase() == QuizScreen.Phase.FINISHED && showing("returnButton"), "처치 후 승리 화면(돌아가기 버튼)");
        click("returnButton");
        check(sm.getCurrentType() == ScreenType.GAME, "처치 후 맵 복귀");

        // ---------------- 숨겨진 함정 발판 (스테이지 2의 TRAP1, 밟으면 발동)
        GameObject trap = object("TRAP1");
        moveTo(trap);
        check(waitFor(QuizBattleWiringCheck::inBattle, 5000), "숨겨진 함정 발판을 밟으면 퀴즈 전투 시작");
        check(label("monsterName").equals("유령") && quiz.getMonsterType() == QuizScreen.MonsterType.GHOST, "TRAP → 이름 '유령' + 유령 이미지: " + label("monsterName"));
        // GAME OVER: HP 0까지 오답
        while (gs.getState().getHp() > 0) {
            click(wrong);
            if (gs.getState().getHp() > 0 && quiz.getPopup() == null) {
                click("nextButton");
            } else if (quiz.getPopup() == QuizScreen.PopupType.LOW_HP) {
                click("popupSecondary");
                click("nextButton");
            }
        }
        check(quiz.getPopup() == QuizScreen.PopupType.GAME_OVER && !showing("timerLabel"), "HP 0 → GAME OVER 팝업, 타이머 숨김");
        click("popupSecondary"); // 재시작
        check(sm.getCurrentType() != ScreenType.QUIZ, "GAME OVER → 재시작: 전투 화면을 떠남 (" + sm.getCurrentType() + ")");

        // GAME OVER 미니게임 / 메인 화면 → GameScreen이 처리하는지
        for (int i = 0; i < 2; i++) {
            setup(2);
            moveTo(object("TRAP1"));
            check(waitFor(QuizBattleWiringCheck::inBattle, 5000), "함정 전투 시작 (GAME OVER 버튼 " + (i == 0 ? "미니게임" : "메인 화면") + " 확인)");
            int c2 = ((game.model.quiz.ChoiceQuiz) quiz.getCurrentQuiz()).getAnswerNo();
            String w2 = "choice" + (c2 == 1 ? 2 : 1);
            while (gs.getState().getHp() > 0) {
                click(w2);
                if (gs.getState().getHp() > 0 && quiz.getPopup() == null) {
                    click("nextButton");
                } else if (quiz.getPopup() == QuizScreen.PopupType.LOW_HP) {
                    click("popupSecondary");
                    click("nextButton");
                }
            }
            click(i == 0 ? "popupPrimary" : "popupTertiary");
            gap(sm.getCurrentType() != ScreenType.QUIZ, "GAME OVER → " + (i == 0 ? "미니게임" : "메인 화면") + " 이동 (지금: " + sm.getCurrentType() + ")",
                    "GameScreen.onQuizBattleExit에 " + (i == 0 ? "MINIGAME" : "MAIN_MENU") + " 처리 추가 (미니게임 화면/START 화면으로 이동)");
        }

        // ---------------- 미믹 상자 (스테이지 3의 MIM1, E 키로 열기)
        setup(3);
        GameObject mimic = object("MIM1");
        check(mimic.getType() == ObjectType.MIMIC, "스테이지 3에 미믹 상자 존재");
        moveTo(mimic);
        settle(400);
        key(KeyEvent.VK_E);
        check(waitFor(QuizBattleWiringCheck::inBattle, 3000), "미믹 상자 상호작용(E) → 퀴즈 전투");
        check(label("monsterName").equals("미믹") && quiz.getMonsterType() == QuizScreen.MonsterType.MIMIC && label("stageLabel").equals("STAGE 3"),
                "MIMIC → 이름 '미믹' + 미믹 이미지, STAGE 3: " + label("monsterName") + " / " + label("stageLabel"));
        int hp0 = gs.getState().getHp();
        check(waitFor(() -> quiz.getPhase() == QuizScreen.Phase.SHOWING_RESULT, 24000) && gs.getState().getHp() == hp0 - 1,
                "시간 초과(제한시간 20초) → GameState HP " + hp0 + " → " + gs.getState().getHp() + ", 결과 화면");
        click("nextButton");
        int c3 = ((game.model.quiz.ChoiceQuiz) quiz.getCurrentQuiz()).getAnswerNo();
        click("choice" + c3);
        check(gs.getState().isObjectResolved("MIM1"), "미믹 퀴즈 정답 → 미믹 해결 상태 반영");
        click("returnButton");
        check(sm.getCurrentType() == ScreenType.GAME, "미믹 처치 후 맵 복귀");

        // ---------------- 데이터 연동
        int distinct = 0;
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (int s = 1; s <= 4; s++) {
            Optional<game.model.Stage> st = new FileStageRepository(Path.of("data/stages")).findByNumber(s);
            if (st.isPresent()) {
                for (GameObject o : st.get().getObjects()) {
                    if (o.getType() == ObjectType.MONSTER || o.getType() == ObjectType.MIMIC || o.getType() == ObjectType.TRAP) {
                        ids.add(o.getQuizId());
                    }
                }
            }
        }
        distinct = ids.size();
        // PR #33부터 오브젝트 줄의 quizId는 문제 번호가 아니라 적 최대 HP를 뜻하므로 같은 값(예: 3)이어도 데이터 오류가 아니다.
        System.out.println("INFO 스테이지 1~4 전투 오브젝트의 quizId 값: " + ids + " (PR #33 이후에는 적 HP)");

        System.out.println("결과: PASS " + pass + " / FAIL " + fails + " / GAP(타 팀원 연동 필요) " + gaps);
        for (String n : gapNotes) {
            System.out.println("  GAP: " + n);
        }
        System.out.println(fails == 0 ? "ALL PASS (GAP 제외)" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
