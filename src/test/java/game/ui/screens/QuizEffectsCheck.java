package game.ui.screens;

import game.common.ScreenType;
import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.ScreenManager;
import game.ui.components.SketchButton;
import game.ui.components.SketchRichText;

import javax.swing.AbstractButton;
import java.awt.CardLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 퀴즈 화면의 이번 개선 기능을 실제 화면(창)에서 확인한다: 보기 번호, 숫자 키 제출(일반·키패드)과 중복 방지,
 * 제출 직후 정답·오답 색, 공격·피격·회복 애니메이션과 피해량·회복량 숫자, 타이머 선, 일시정지 시간 유지,
 * 단답형 입력창, 결과 화면의 정답·내가 고른 답 구분, 캐릭터 배치.
 *
 * PASS / FAIL / SKIP을 구분해 출력한다. 화면이 없는 환경이나 창에 키보드 초점이 없어 실행하지 못한 항목은 SKIP이며 PASS로 세지 않는다.
 * 실행: java -Dfile.encoding=UTF-8 -cp "out;src/main/resources;..." game.ui.screens.QuizEffectsCheck [quiz.txt 경로]
 */
public final class QuizEffectsCheck {

    private static int pass;
    private static int fail;
    private static int skip;
    private static JFrame frame;
    private static QuizScreen screen;
    private static Container root;
    private static final List<String> answers = new ArrayList<>();
    private static final List<String> events = new ArrayList<>();
    /** 시험용 전투: 플레이어 HP를 직접 갖고, 오답·시간 초과면 1 줄이고 화면에 판정 결과를 돌려준다 */
    private static int playerHp = 3;
    private static int monsterHp = 0;
    private static int monsterMax = 0;

    private static void check(boolean ok, String message) {
        System.out.println((ok ? "PASS " : "FAIL ") + message);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
    }

    private static void skip(String message) {
        System.out.println("SKIP " + message);
        skip++;
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void sleep(int ms) throws Exception {
        Thread.sleep(ms);
        edt(() -> { });
    }

    /** 연출(정답·오답 색, 애니메이션)이 모두 끝날 때까지 기다린다 */
    private static void waitIdle() throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(30);
            boolean[] busy = new boolean[1];
            edt(() -> busy[0] = screen.isFeedbackActive() || screen.isEffectRunning());
            if (!busy[0]) {
                return;
            }
        }
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

    private static SketchButton choice(int n) {
        return (SketchButton) find(root, "choice" + n);
    }

    private static Rectangle rect(String name) {
        Component c = find(root, name);
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
    }

    private static boolean visible(String name) {
        Component c = find(root, name);
        return c != null && c.isShowing();
    }

    private static void open() throws Exception {
        edt(() -> {
            frame = new JFrame("quiz-effects");
            CardLayout cl = new CardLayout();
            JPanel c = new JPanel(cl);
            c.setPreferredSize(new Dimension(1280, 720));
            ScreenManager sm = new ScreenManager(c, cl);
            screen = new QuizScreen(sm);
            sm.register(ScreenType.QUIZ, screen);
            frame.add(c);
            frame.pack();
            frame.setLocation(0, 0);
            frame.setVisible(true);
            sm.show(ScreenType.QUIZ);
            root = frame.getContentPane();
            screen.setQuizListener(new QuizScreen.QuizListener() {
                @Override
                public void onAnswerSubmitted(Quiz quiz, String answer) {
                    answers.add(answer);
                    boolean ok = quiz.isCorrect(answer);
                    if (!ok) {
                        playerHp = Math.max(0, playerHp - 1);
                        screen.updateHp(playerHp, 3, monsterHp, monsterMax);
                    } else if (monsterMax > 0) {
                        monsterHp--;
                        screen.updateHp(playerHp, 3, monsterHp, monsterMax);
                    }
                    screen.showResult(ok ? QuizScreen.ResultType.CORRECT : QuizScreen.ResultType.WRONG, "", true);
                }

                @Override
                public void onTimeExpired(Quiz quiz) {
                    events.add("timeout");
                    playerHp = Math.max(0, playerHp - 1);
                    screen.updateHp(playerHp, 3, monsterHp, monsterMax);
                    screen.showResult(QuizScreen.ResultType.TIMEOUT, "", true);
                }

                @Override
                public void onNextRequested() {
                    events.add("next");
                }
            });
        });
        Thread.sleep(500);
    }

    private static void begin(Quiz q, int limit, int hp, QuizScreen.MonsterType type) throws Exception {
        playerHp = hp;
        monsterHp = 0;
        monsterMax = 0;
        answers.clear();
        events.clear();
        edt(() -> {
            screen.resetBattle();
            screen.setMonsterType(type);
            screen.setCombatantNames("용사", type.displayName());
            screen.updateHp(hp, 3, 0, 0);
            screen.showQuiz(q, 1, limit);
        });
        sleep(250);
    }

    private static Quiz choiceQuiz(List<Quiz> all, int minChoiceLen, boolean code) {
        for (Quiz q : all) {
            if (q instanceof ChoiceQuiz cq && q.getQuestion().contains("\n") == code) {
                int max = cq.getChoices().stream().mapToInt(String::length).max().orElse(0);
                if (max >= minChoiceLen) {
                    return q;
                }
            }
        }
        throw new IllegalStateException("조건에 맞는 문제가 없습니다");
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: 화면이 없는 환경에서는 실행하지 않습니다. (PASS로 세지 않음)");
            return;
        }
        List<Quiz> all = new game.repository.file.FileQuizRepository(
                java.nio.file.Path.of(args.length > 0 ? args[0] : "data/quiz.txt")).findAll();
        Quiz shortQ = choiceQuiz(all, 1, false);
        Quiz longQ = choiceQuiz(all, 20, false);
        ChoiceQuiz sq = (ChoiceQuiz) shortQ;
        ChoiceQuiz lq = (ChoiceQuiz) longQ;
        Quiz inQ = new InputQuiz(900, 1, "Java에서 문자열을 저장하는 클래스 이름은?", "String 클래스입니다.", "String");
        int sqWrong = sq.getAnswerNo() % 4 + 1;
        var G = QuizScreen.MonsterType.GOLEM;
        open();

        // ---- 1. 보기 번호
        begin(shortQ, 20, 3, G);
        boolean grid = screen.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.GRID;
        boolean numbered = true;
        for (int i = 1; i <= 4; i++) {
            numbered &= String.valueOf(i).equals(choice(i).getLeadingLabel());
        }
        check(numbered, "객관식 보기 1~4에 번호가 붙는다 (" + (grid ? "2×2" : "4×1") + ")");
        Rectangle b1 = rect("choice1");
        Rectangle b2 = rect("choice2");
        Rectangle b3 = rect("choice3");
        Rectangle b4 = rect("choice4");
        if (grid) {
            check(b1.y == b2.y && b3.y == b4.y && b1.x < b2.x && b1.x == b3.x && b3.y > b1.y,
                    "2×2: 왼쪽 위부터 오른쪽으로 1,2 / 3,4 순서");
        } else {
            check(b1.y < b2.y && b2.y < b3.y && b3.y < b4.y && b1.x == b4.x, "4×1: 위에서 아래로 1,2,3,4 순서");
        }
        boolean fits = true;
        for (int i = 1; i <= 4; i++) {
            fits &= !choice(i).isTextTruncated() && choice(i).textLineCount() <= 1;
        }
        check(fits, "번호가 있어도 본문이 한 줄로 잘리지 않고 들어간다");
        check(choice(1).leadingWidth() > 0 && choice(1).leadingWidth() == choice(4).leadingWidth(), "번호 칸 너비가 모든 보기에서 같다 (" + choice(1).leadingWidth() + "px)");
        begin(longQ, 20, 3, G);
        check(screen.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.WIDE, "긴 보기가 있는 문제는 번호를 뺀 폭 기준으로 4×1");
        boolean sameFont = true;
        float size = choice(1).fittedFont().getSize2D();
        for (int i = 2; i <= 4; i++) {
            sameFont &= Math.abs(choice(i).fittedFont().getSize2D() - size) < 0.01;
        }
        check(sameFont, "보기마다 글자 크기를 따로 줄이지 않는다 (" + size + "px)");

        // ---- 2. 숫자 키 (일반·키패드 바인딩, 중복 방지)
        boolean normalKeys = true;
        boolean padKeys = true;
        JComponent anyBound = null;
        for (Component c : allComponents(root)) {
            if (c instanceof JComponent jc && jc.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0)) != null) {
                anyBound = jc;
            }
        }
        for (int n = 1; n <= 4 && anyBound != null; n++) {
            normalKeys &= anyBound.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, 0)) != null;
            padKeys &= anyBound.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + n, 0)) != null;
        }
        check(anyBound != null && normalKeys && padKeys, "숫자 키 1~4(일반·키패드)가 InputMap에 연결되어 있다");
        check(anyBound != null && anyBound.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0)) == null,
                "5 이상의 숫자 키는 쓰지 않는다");

        begin(shortQ, 20, 3, G);
        edt(() -> {
            for (int i = 0; i < 6; i++) {
                screen.pressNumberKeyForTest(2); // 키를 누르고 있어 같은 키 입력이 반복되는 경우
            }
        });
        sleep(300);
        check(answers.equals(List.of("2")), "숫자 키 2를 길게 눌러도 한 번만 제출된다: " + answers);
        waitIdle();
        edt(() -> screen.pressNumberKeyForTest(3));
        sleep(100);
        check(answers.size() == 1, "결과 화면에서는 숫자 키로 제출되지 않는다");
        edt(() -> {
            screen.releaseNumberKeyForTest(2);
            screen.releaseNumberKeyForTest(3);
        });

        begin(shortQ, 20, 3, G);
        edt(() -> screen.pressNumberKeyForTest(3));
        waitIdle();
        check(answers.equals(List.of("3")), "숫자 키 3 → 3번 보기 제출 (일반 키와 키패드는 같은 처리)");
        begin(shortQ, 20, 3, G);
        edt(() -> {
            screen.openPause();
            screen.pressNumberKeyForTest(1);
        });
        sleep(150);
        check(answers.isEmpty() && screen.getPopup() == QuizScreen.PopupType.PAUSE, "일시정지 중에는 숫자 키 제출이 막힌다");
        edt(() -> screen.releaseNumberKeyForTest(1));

        // 키보드 이벤트가 실제로 도착하는지 (창에 초점이 있을 때만 의미가 있다)
        begin(shortQ, 20, 3, G);
        boolean[] focused = new boolean[1];
        edt(() -> focused[0] = frame.isActive());
        if (focused[0]) {
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_2);
            robot.keyRelease(KeyEvent.VK_2);
            sleep(400);
            check(answers.equals(List.of("2")), "실제 키보드 2 입력 → 2번 보기 제출: " + answers);
            waitIdle();
            begin(shortQ, 20, 3, G);
            robot.keyPress(KeyEvent.VK_NUMPAD4);
            robot.keyRelease(KeyEvent.VK_NUMPAD4);
            sleep(400);
            check(answers.equals(List.of("4")), "실제 키패드 4 입력 → 4번 보기 제출: " + answers);
            waitIdle();
        } else {
            skip("실제 키보드 입력(Robot): 창에 키보드 초점이 없어 확인하지 못했다 (hook 기반 검사는 위에서 PASS)");
        }

        // ---- 3. 단답형: 숫자 입력, 입력창 크기, 안내
        begin(inQ, 20, 3, G);
        Rectangle in = rect("inputFrame");
        Rectangle sub = rect("inputSubmitButton");
        check(in.height >= 50 && in.height <= 56, "입력창 높이 " + in.height + "px (50~56)");
        check(sub.height >= 46 && sub.height <= 50, "제출 버튼 높이 " + sub.height + "px (46~50)");
        check(visible("inputHint") && "Enter 키로 제출할 수 있습니다.".equals(((game.ui.components.SketchLabel) find(root, "inputHint")).getText()),
                "제출 버튼 아래 안내 'Enter 키로 제출할 수 있습니다.'");
        Rectangle hint = rect("inputHint");
        check(hint.y >= sub.y + sub.height && hint.y + hint.height <= rect("quizFrame").y + rect("quizFrame").height, "안내는 제출 버튼 아래, 문제 틀 안");
        check("정답을 입력하세요.".equals(QuizScreen.ANSWER_HINT), "입력창 placeholder: 정답을 입력하세요.");
        JTextField tf = (JTextField) find(root, "inputField");
        check(tf.getBorder().getBorderInsets(tf).left >= 10 && tf.getBorder().getBorderInsets(tf).right >= 10, "입력창 내부 좌우 여백 " + tf.getBorder().getBorderInsets(tf).left + "px");
        edt(() -> screen.pressNumberKeyForTest(1));
        sleep(150);
        check(answers.isEmpty(), "단답형에서는 숫자 키가 보기 제출로 처리되지 않는다");
        edt(() -> screen.releaseNumberKeyForTest(1));
        boolean[] hasFocus = new boolean[1];
        edt(() -> hasFocus[0] = tf.isFocusOwner());
        if (focused[0]) {
            check(hasFocus[0], "단답형 문제가 뜨면 입력창에 초점이 간다");
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_1);
            robot.keyRelease(KeyEvent.VK_1);
            robot.keyPress(KeyEvent.VK_2);
            robot.keyRelease(KeyEvent.VK_2);
            sleep(300);
            check("12".equals(tf.getText()) && answers.isEmpty(), "단답형 입력 중 숫자 키는 글자로 입력된다: '" + tf.getText() + "'");
        } else {
            skip("단답형 초점·숫자 입력(Robot): 창에 키보드 초점이 없어 확인하지 못했다");
        }
        edt(() -> tf.setText(""));
        edt(tf::postActionEvent);
        sleep(100);
        check(answers.isEmpty(), "빈 답은 제출되지 않는다 (기존 동작 유지)");
        edt(() -> {
            tf.setText("string");
            tf.postActionEvent();
            tf.postActionEvent();
        });
        waitIdle();
        check(answers.equals(List.of("string")), "Enter로 제출, 중복 제출은 한 번만: " + answers);

        // ---- 4. 정답·오답 색 피드백 + 타이머 정지 + 결과 지연
        begin(shortQ, 20, 3, G);
        long[] t0 = new long[1];
        boolean[] snap = new boolean[8];
        Color[][] colors = new Color[1][];
        edt(() -> {
            t0[0] = System.nanoTime();
            choice(sq.getAnswerNo()).doClick(0);
            snap[0] = screen.isFeedbackActive();
            snap[1] = screen.getPhase() == QuizScreen.Phase.WAITING_RESULT;
            snap[2] = screen.isCountdownRunning();
            colors[0] = new Color[] {choice(sq.getAnswerNo()).getFeedbackColor(), choice(sqWrong).getFeedbackColor()};
        });
        check(snap[0] && snap[1], "정답 제출 직후: 연출 중이고 결과 화면 전환은 잠깐 미뤄진다");
        check(!snap[2], "답안 제출 즉시 문제 타이머가 멈춘다");
        check(colors[0][0] != null && colors[0][0].getGreen() > colors[0][0].getRed() && colors[0][1] == null,
                "채점 결과가 나오면 선택한 보기가 연한 초록, 나머지는 칠하지 않는다: " + colors[0][0]);
        waitIdle();
        long elapsedMs = (System.nanoTime() - t0[0]) / 1_000_000;
        check(screen.getPhase() == QuizScreen.Phase.SHOWING_RESULT && answers.size() == 1, "연출 뒤 결과 화면으로 넘어가고 판정은 한 번이다");
        check(elapsedMs < 1300, "결과 화면까지 걸린 시간 " + elapsedMs + "ms (애니메이션 때문에 과도하게 지연되지 않음)");

        begin(shortQ, 20, 3, G);
        edt(() -> {
            choice(sqWrong).doClick(0);
            colors[0] = new Color[] {choice(sqWrong).getFeedbackColor()};
        });
        check(colors[0][0] != null && colors[0][0].getRed() > colors[0][0].getGreen(), "오답 제출 직후 선택한 보기가 연한 빨강: " + colors[0][0]);
        edt(() -> {
            choice(1).doClick(0);
            choice(2).doClick(0);
            screen.pressNumberKeyForTest(4);
        });
        waitIdle();
        check(answers.equals(List.of(String.valueOf(sqWrong))) && playerHp == 2, "연출 중 추가 입력은 무시된다: 제출 " + answers + ", HP " + playerHp + " (한 번만 차감)");
        edt(() -> screen.releaseNumberKeyForTest(4));

        // ---- 5. 정답 애니메이션: 주인공이 앞으로, 몬스터가 맞음
        begin(shortQ, 20, 3, G);
        double[] maxPlayerDx = new double[1];
        double[] maxMonsterFlash = new double[1];
        edt(() -> choice(sq.getAnswerNo()).doClick(0));
        for (int i = 0; i < 30; i++) {
            Thread.sleep(15);
            edt(() -> {
                maxPlayerDx[0] = Math.max(maxPlayerDx[0], screen.characterOffset(true));
                maxMonsterFlash[0] = Math.max(maxMonsterFlash[0], screen.characterFlash(false));
            });
        }
        check(maxPlayerDx[0] >= 8 && maxPlayerDx[0] <= 16, "정답: 주인공이 몬스터 쪽으로 8~16px 이동 (최대 " + String.format("%.1f", maxPlayerDx[0]) + "px)");
        check(maxMonsterFlash[0] > 0.1, "정답: 몬스터가 붉게 점멸 (" + String.format("%.2f", maxMonsterFlash[0]) + ")");
        check(screen.floatingTexts().isEmpty() || !screen.floatingTexts().contains("-1"), "몬스터 HP가 없으면 가짜 피해량을 표시하지 않는다: " + screen.floatingTexts());
        waitIdle();
        check(screen.characterOffset(true) == 0 && screen.characterOffset(false) == 0 && screen.characterFlash(false) == 0
                && screen.characterGlow(true) == 0 && screen.floatingTexts().isEmpty() && !screen.isEffectRunning(),
                "애니메이션이 끝나면 위치·색 효과·숫자가 모두 원래대로 돌아온다");

        // ---- 6. 오답 애니메이션: 몬스터가 앞으로, 주인공이 맞고 실제 HP 감소량 표시
        begin(shortQ, 20, 3, G);
        double[] minMonsterDx = new double[1];
        double[] maxPlayerFlash = new double[1];
        boolean[] sawMinus = new boolean[1];
        edt(() -> choice(sqWrong).doClick(0));
        for (int i = 0; i < 40; i++) {
            Thread.sleep(15);
            edt(() -> {
                minMonsterDx[0] = Math.min(minMonsterDx[0], screen.characterOffset(false));
                maxPlayerFlash[0] = Math.max(maxPlayerFlash[0], screen.characterFlash(true));
                sawMinus[0] |= screen.floatingTexts().contains("-1");
            });
        }
        check(minMonsterDx[0] <= -8 && minMonsterDx[0] >= -16, "오답: 몬스터가 주인공 쪽으로 8~16px 이동 (최대 " + String.format("%.1f", minMonsterDx[0]) + "px)");
        check(maxPlayerFlash[0] > 0.1, "오답: 주인공이 붉게 점멸 (" + String.format("%.2f", maxPlayerFlash[0]) + ")");
        check(sawMinus[0] && playerHp == 2, "실제 HP 감소량 -1을 주인공 옆에 표시 (HP " + playerHp + ")");
        waitIdle();
        check(!screen.isEffectRunning() && screen.floatingTexts().isEmpty(), "표시한 숫자는 약 0.7초 안에 사라진다");

        // ---- 7. 시간 초과 피격
        begin(shortQ, 1, 3, G);
        boolean[] sawHit = new boolean[1];
        boolean[] sawNeg = new boolean[1];
        for (int i = 0; i < 90; i++) {
            Thread.sleep(25);
            edt(() -> {
                sawHit[0] |= screen.characterFlash(true) > 0.1;
                sawNeg[0] |= screen.floatingTexts().contains("-1");
            });
        }
        check(events.contains("timeout") && sawHit[0] && sawNeg[0], "시간 초과: 피격 연출과 -1 표시 (timeout=" + events.contains("timeout") + ")");
        check(choice(1).getFeedbackColor() == null && choice(2).getFeedbackColor() == null, "시간 초과에는 선택한 보기가 없어 색을 칠하지 않는다");
        waitIdle();

        // ---- 8. 실제 몬스터 HP가 있을 때만 몬스터 피해량 (구조 확인)
        begin(shortQ, 20, 3, G);
        edt(() -> {
            monsterHp = 3;
            monsterMax = 3;
            screen.updateHp(playerHp, 3, 3, 3);
        });
        boolean[] sawMonsterMinus = new boolean[1];
        edt(() -> choice(sq.getAnswerNo()).doClick(0));
        for (int i = 0; i < 20; i++) {
            Thread.sleep(15);
            edt(() -> sawMonsterMinus[0] |= screen.floatingTexts().contains("-1"));
        }
        check(sawMonsterMinus[0], "몬스터 HP가 실제로 줄어든 경우에는 같은 구조로 몬스터 위에 피해량을 표시한다");
        waitIdle();

        // ---- 9. 회복: 실제로 HP가 늘었을 때만
        begin(shortQ, 20, 1, G);
        boolean[] sawHeal = new boolean[1];
        double[] glow = new double[1];
        edt(() -> screen.updateHp(2, 3, 0, 0));
        for (int i = 0; i < 20; i++) {
            Thread.sleep(15);
            edt(() -> {
                sawHeal[0] |= screen.floatingTexts().contains("+1");
                glow[0] = Math.max(glow[0], screen.characterGlow(true));
            });
        }
        check(sawHeal[0] && glow[0] > 0.1, "회복 성공(HP 1→2): 초록 효과와 +1 표시 (glow " + String.format("%.2f", glow[0]) + ")");
        waitIdle();
        edt(() -> screen.updateHp(2, 3, 0, 0));
        sleep(100);
        check(screen.floatingTexts().isEmpty(), "HP가 그대로면 회복량을 표시하지 않는다");
        edt(() -> screen.updateHp(3, 3, 0, 0));
        waitIdle();
        edt(() -> screen.updateHp(3, 3, 0, 0));
        sleep(100);
        check(screen.floatingTexts().isEmpty(), "이미 최대 HP이면 회복량을 표시하지 않는다");

        // ---- 10. 타이머 선: 남은 시간 비율, 5초 이하 빨강, 일시정지 유지·재개
        begin(shortQ, 20, 3, G);
        double[] fr = new double[3];
        boolean[] warn = new boolean[2];
        edt(() -> {
            fr[0] = screen.timerBarFraction();
            warn[0] = screen.timerBarWarning();
        });
        check(fr[0] > 0.9 && !warn[0], "문제 시작: 선이 가득 차 있고 경고색이 아니다 (" + String.format("%.2f", fr[0]) + ")");
        sleep(1600);
        edt(() -> screen.openPause());
        sleep(60);
        edt(() -> fr[1] = screen.timerBarFraction());
        sleep(900);
        edt(() -> fr[2] = screen.timerBarFraction());
        check(fr[1] < fr[0] && Math.abs(fr[1] - fr[2]) < 0.0005, "일시정지 중 진행선이 멈춘다 (" + String.format("%.4f → %.4f", fr[1], fr[2]) + ")");
        check("PAUSED".equals(screen.timerText()), "일시정지 중 문구 PAUSED");
        edt(() -> screen.openPause()); // 이미 열려 있으면 무시
        Component resume = find(root, "popupPrimary");
        edt(() -> ((AbstractButton) resume).doClick(0));
        double[] after = new double[1];
        sleep(300);
        edt(() -> after[0] = screen.timerBarFraction());
        double expected = fr[2] - 0.3 / 20.0;
        check(after[0] < fr[2] && Math.abs(after[0] - expected) < 0.03,
                "재개하면 멈춘 시점부터 이어서 줄어든다 (멈춤 " + String.format("%.3f", fr[2]) + " → 재개 후 " + String.format("%.3f", after[0]) + ", 예상 " + String.format("%.3f", expected) + ")");
        begin(shortQ, 5, 3, G);
        edt(() -> warn[1] = screen.timerBarWarning());
        check(warn[1] && screen.timerText().equals("TIME 5"), "남은 시간 5초 이하: 선과 TIME 문구가 빨간색 계열 (" + screen.timerText() + ")");
        edt(() -> choice(sq.getAnswerNo()).doClick(0));
        double[] frozen = new double[2];
        edt(() -> frozen[0] = screen.timerBarFraction());
        sleep(400);
        edt(() -> frozen[1] = screen.timerBarFraction());
        check(Math.abs(frozen[0] - frozen[1]) < 0.0005 || screen.getPhase() == QuizScreen.Phase.SHOWING_RESULT,
                "제출한 뒤 진행선은 더 줄어들지 않는다");
        waitIdle();

        // ---- 11. 결과 화면: 정답 초록, 내가 고른 답 빨강, 번호 일치
        begin(shortQ, 20, 3, G);
        edt(() -> choice(sqWrong).doClick(0));
        waitIdle();
        List<SketchRichText.Para> paras = ((SketchRichText) find(root, "questionArea")).getParas();
        SketchRichText.Para ans = paras.get(0);
        SketchRichText.Para mine = paras.get(1);
        check(ans.kind() == SketchRichText.Kind.BOX && new Color(0xE7, 0xF1, 0xE6).equals(ans.background()), "정답 영역: 연한 초록 #E7F1E6 바탕");
        check(mine.kind() == SketchRichText.Kind.BOX && new Color(0xFA, 0xE6, 0xE6).equals(mine.background()), "내가 고른 답 영역: 연한 빨강 #FAE6E6 바탕");
        check(ans.text().startsWith("정답 : " + sq.getAnswerNo() + ". " + sq.getChoices().get(sq.getAnswerNo() - 1)),
                "결과의 정답 번호·내용이 보기 번호와 일치: " + ans.text());
        check(mine.text().startsWith("내가 고른 답 : " + sqWrong + ". " + sq.getChoices().get(sqWrong - 1)), "내가 고른 답 번호·내용: " + mine.text());
        check(paras.stream().anyMatch(p -> p.kind() == SketchRichText.Kind.BOLD && p.text().equals("해설")), "'해설' 제목은 Bold");
        begin(shortQ, 20, 3, G);
        edt(() -> choice(sq.getAnswerNo()).doClick(0));
        waitIdle();
        paras = ((SketchRichText) find(root, "questionArea")).getParas();
        check(paras.stream().filter(p -> p.kind() == SketchRichText.Kind.BOX).count() == 1, "정답 화면에는 정답 영역만 표시한다");
        Rectangle nextB = rect("nextButton");
        check(visible("nextButton") && nextB.y + nextB.height <= rect("quizFrame").y + rect("quizFrame").height, "다음 문제 버튼은 기존 하단 위치, 틀 안");
        begin(inQ, 20, 3, G);
        edt(() -> {
            tf.setText("abc");
            tf.postActionEvent();
        });
        waitIdle();
        paras = ((SketchRichText) find(root, "questionArea")).getParas();
        check(paras.get(1).text().equals("내가 쓴 답 : abc") && paras.get(1).background().equals(new Color(0xFA, 0xE6, 0xE6)), "단답형도 같은 디자인: " + paras.get(1).text());

        // ---- 12. 화면 전환·재시작 시 연출 정리, 이전 전투가 남지 않음
        for (int round = 0; round < 3; round++) {
            begin(shortQ, 20, 3, G);
            edt(() -> choice(sqWrong).doClick(0));
            sleep(60);
            edt(() -> screen.resetBattle()); // 연출 도중 전투를 끝낸다 / 새 전투를 시작한다
            sleep(30);
            boolean[] clean = new boolean[1];
            edt(() -> clean[0] = !screen.isFeedbackActive() && !screen.isEffectRunning() && screen.floatingTexts().isEmpty()
                    && screen.characterOffset(true) == 0 && screen.characterOffset(false) == 0
                    && screen.characterFlash(true) == 0 && screen.getPhase() == QuizScreen.Phase.READY);
            check(clean[0], "연출 도중 새 전투 시작(" + (round + 1) + "번째): 타이머·위치·색·숫자가 모두 정리된다");
            sleep(400);
            check(screen.getPhase() == QuizScreen.Phase.READY, "예약돼 있던 결과 화면이 나중에 튀어나오지 않는다");
        }
        begin(shortQ, 20, 3, G);
        edt(() -> choice(sqWrong).doClick(0));
        sleep(60);
        edt(() -> screen.onExit()); // 다른 화면으로 나간다
        boolean[] left = new boolean[1];
        edt(() -> left[0] = !screen.isFeedbackActive() && !screen.isEffectRunning() && screen.characterOffset(true) == 0 && screen.characterOffset(false) == 0);
        check(left[0], "화면을 떠나면(onExit) 남은 연출 타이머가 정리된다");

        // ---- 13. 정답·오답 연출 뒤 HP 경고·GAME OVER 순서
        begin(shortQ, 20, 1, G);
        edt(() -> {
            screen.updateHp(1, 3, 0, 0);
        });
        edt(() -> choice(sqWrong).doClick(0)); // HP 1 → 0
        sleep(60);
        check(screen.getPopup() == null, "GAME OVER 팝업은 정답·오답 연출 중에는 뜨지 않고");
        edt(() -> screen.showGameOver("체력이 모두 소진되었습니다."));
        waitIdle();
        sleep(100);
        check(screen.getPopup() == QuizScreen.PopupType.GAME_OVER, "연출이 끝난 뒤에 뜬다");

        // ---- 14. 캐릭터 배치: 같은 기준선, 유령은 위, 문제 틀 침범 없음, HP가 없어도 위치 고정
        QuizScreen.MonsterType[] kinds = QuizScreen.MonsterType.values();
        int[] bottoms = new int[kinds.length];
        for (int k = 0; k < kinds.length; k++) {
            final int kk = k;
            begin(shortQ, 20, 3, kinds[k]);
            edt(() -> bottoms[kk] = rect("monsterView").y + rect("monsterView").height);
        }
        int playerBottom = rect("playerView").y + rect("playerView").height;
        // 주인공은 그대로(발 y=660), 골렘·미믹은 이전 시안처럼 위쪽(아래 끝 y≈590)에 둔다: 억지로 바닥을 맞추지 않는다. 유령은 더 높이 뜬다
        check(playerBottom == 660 && Math.abs(bottoms[0] - 590) <= 2 && Math.abs(bottoms[1] - 590) <= 2,
                "주인공 발 y=660, 골렘·미믹 아래 끝 y≈590: " + playerBottom + "," + bottoms[0] + "," + bottoms[1]);
        check(bottoms[2] < bottoms[0] - 20, "유령은 공중에 떠 있다 (아래 끝 " + bottoms[2] + ")");
        Rectangle fr2 = rect("quizFrame");
        int pad = (int) Math.round(24.0 * fr2.width / 616.0);
        Rectangle pv = rect("playerView");
        Rectangle mv = rect("monsterView");
        check(pv.x + pv.width - pad <= fr2.x && mv.x + pad >= fr2.x + fr2.width, "캐릭터 그림(연출 여유 제외)이 문제 틀을 침범하지 않는다");
        Rectangle nameBefore = rect("monsterName");
        Rectangle viewBefore = rect("monsterView");
        edt(() -> screen.updateHp(3, 3, 0, 0));
        sleep(100);
        check(nameBefore.equals(rect("monsterName")) && viewBefore.equals(rect("monsterView")), "몬스터 HP가 없어도(HP 줄 없음) 이름·캐릭터 위치가 그대로다");
        Rectangle logo = rect("logo");
        Rectangle stageFrame = rect("stageFrame");
        check(logo.x >= 0 && logo.x + logo.width <= stageFrame.x && logo.y >= 0, "로고는 헤더 안, STAGE 표시와 겹치지 않는다");

        edt(() -> frame.dispose());
        System.out.println("결과: PASS " + pass + " / FAIL " + fail + " / SKIP " + skip);
        System.exit(fail == 0 ? 0 : 1);
    }

    private static List<Component> allComponents(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            out.add(k);
            if (k instanceof Container cc) {
                out.addAll(allComponents(cc));
            }
        }
        return out;
    }
}
