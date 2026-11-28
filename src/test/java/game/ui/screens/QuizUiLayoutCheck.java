package game.ui.screens;

import game.common.ScreenType;
import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.GameFonts;
import game.ui.ScreenManager;
import game.ui.components.SketchButton;
import game.ui.components.SketchAssets;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 퀴즈 전투 화면 레이아웃 점검 (담당: QuizScreen). 외부 라이브러리 없이 main으로 실행하며, 하나라도 실패하면 종료 코드가 0이 아니다.
 *
 * 확인하는 것: 자동 2×2/4×1 선택, 문제 간 연속 전환, 창 크기 변경, 짧은 문제의 불필요한 스크롤 없음, Q. 시작 위치 통일,
 * 배경색, 오답 시 제출 답 표시, 팝업 배치, 그리고 quiz.txt 전체 문제의 배치(스크롤·코드 줄바꿈이 필요한 문제는 정보로 출력).
 *
 * 실행(프로젝트 루트, 컴파일 후): java -cp "out;src/main/resources" game.ui.screens.QuizUiLayoutCheck [quiz.txt 경로]
 * (Linux/Mac은 classpath 구분자 ':'). 화면이 없는 환경(headless)에서는 건너뛴다.
 */
public class QuizUiLayoutCheck {
    static int fails = 0;
    static JFrame f;
    static QuizScreen s;
    static Container root;
    static final List<String> answers = new ArrayList<>();

    static void check(boolean ok, String m) {
        System.out.println((ok ? "PASS " : "FAIL ") + m);
        if (!ok) {
            fails++;
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

    static Rectangle rect(String name) {
        Component c = find(root, name);
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
    }

    static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void settle() throws Exception {
        Thread.sleep(80);
        edt(() -> { });
        edt(() -> { });
        // 제출 직후 정답·오답 색을 보여 주는 동안(약 0.2초)은 결과 화면 전환이 미뤄진다: 끝날 때까지 기다린다
        for (int i = 0; i < 60 && s != null && s.isFeedbackActive(); i++) {
            Thread.sleep(20);
        }
        edt(() -> { });
    }

    static void open(int w, int h) throws Exception {
        if (f != null) {
            edt(() -> f.dispose());
        }
        edt(() -> {
            f = new JFrame("t");
            CardLayout cl = new CardLayout();
            JPanel c = new JPanel(cl);
            c.setPreferredSize(new Dimension(w, h));
            ScreenManager sm = new ScreenManager(c, cl);
            s = new QuizScreen(sm);
            sm.register(ScreenType.QUIZ, s);
            f.add(c);
            f.pack();
            f.setLocation(0, 0);
            f.setVisible(true);
            sm.show(ScreenType.QUIZ);
            s.setCombatantNames("용사", "골렘");
            s.updateHp(3, 3, 3, 3);
            s.setQuizListener(new QuizScreen.QuizListener() {
                public void onAnswerSubmitted(Quiz q, String a) {
                    answers.add(a);
                }

                public void onTimeExpired(Quiz q) {
                }

                public void onNextRequested() {
                }
            });
            root = f.getContentPane();
        });
        Thread.sleep(400);
        edt(() -> { });
    }

    static void resize(int w, int h) throws Exception {
        edt(() -> {
            ((JComponent) f.getContentPane().getComponent(0)).setPreferredSize(new Dimension(w, h));
            f.getContentPane().setPreferredSize(new Dimension(w, h));
            f.pack();
        });
        settle();
        Thread.sleep(200);
        edt(() -> { });
    }

    static boolean barShowing() {
        return ((JScrollPane) find(root, "bodyScroll")).getVerticalScrollBar().isShowing();
    }

    /** 독립 계산: 모든 보기가 24px 글꼴로 2×2 버튼 안쪽 칸 한 줄에 들어가는가 */
    static boolean expectGrid(ChoiceQuiz q, double scale) {
        Font font = GameFonts.text(Font.PLAIN, (float) (24 * scale));
        FontMetrics fm = new BufferedImage(1, 1, 1).createGraphics().getFontMetrics(font);
        Graphics2D g = new BufferedImage(1, 1, 1).createGraphics();
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        fm = g.getFontMetrics(font);
        double inner = (256 * (1 - 2 * 0.11) - 24 * 1.15) * scale; // 버튼 안쪽 폭 - 왼쪽 번호 칸
        for (String c : q.getChoices()) {
            if (fm.stringWidth(c) > inner) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] a) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: 화면이 없는 환경에서는 실행하지 않습니다.");
            return;
        }
        List<Quiz> all = new game.repository.file.FileQuizRepository(java.nio.file.Path.of(a.length > 0 ? a[0] : "data/quiz.txt")).findAll();
        // 문제 데이터가 바뀌어도 동작하도록 데이터에서 대표 문제를 고른다 (단답형은 데이터에 없을 수 있어 내장 데모 문제를 쓴다)
        List<Quiz> gridList = new ArrayList<>();
        List<Quiz> wideList = new ArrayList<>();
        List<Quiz> codeList = new ArrayList<>();
        for (Quiz q : all) {
            if (!(q instanceof ChoiceQuiz cq)) {
                continue;
            }
            if (q.getQuestion().contains("\n")) {
                codeList.add(q);
            } else if (expectGrid(cq, 1.0)) {
                gridList.add(q);
            } else {
                wideList.add(q);
            }
        }
        Quiz inputDemo = QuizScreenDemo.createDemoQuizzes().stream().filter(q -> q instanceof InputQuiz && q.getQuestion().contains("\n")).findFirst().get();
        java.util.Map<Integer, Quiz> pick = java.util.Map.of(8, gridList.get(0), 11, wideList.get(0), 18, gridList.get(1), 7, inputDemo,
                36, codeList.get(0), 15, codeList.get(1), 3, codeList.get(2));
        java.util.function.IntFunction<Quiz> byId = pick::get;
        open(1280, 720);

        // ---- 기본값은 자동
        check(s.getChoiceLayout() == QuizScreen.ChoiceLayout.AUTO, "기본 보기 배치는 AUTO");

        // ---- 자동 판단: 짧은 보기 → 2×2, 하나라도 길면 4×1
        Quiz shortQ = byId.apply(8);
        Quiz longQ = byId.apply(11);
        edt(() -> s.showQuiz(shortQ, 1, 20));
        settle();
        check(s.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.GRID, "짧은 보기(SRP/OCP/DRY/KISS) → 2×2: " + s.getAppliedChoiceLayout());
        check(((SketchButton) find(root, "choice1")).getImageFile().equals(SketchAssets.BUTTON_SQUARE), "2×2는 button_square.png 사용");
        edt(() -> s.showQuiz(longQ, 1, 20));
        settle();
        check(s.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.WIDE, "긴 보기 하나 포함 → 4×1: " + s.getAppliedChoiceLayout());
        check(((SketchButton) find(root, "choice1")).getImageFile().equals(SketchAssets.BUTTON_WIDE), "4×1은 button_wide.png 사용");
        // 보기 하나만 길어도 4×1: 짧은 보기 3개 + 긴 보기 1개 (id 10 은 앞 보기가 짧고 뒤가 긴 문제인지 일반 검사로 확인)
        int onlyOneLong = 0;
        boolean allOk = true;
        for (Quiz q : all) {
            if (!(q instanceof ChoiceQuiz cq)) {
                continue;
            }
            edt(() -> s.showQuiz(q, 1, 20));
            settle();
            boolean expectGrid = expectGrid(cq, 1.0);
            QuizScreen.ChoiceLayout got = s.getAppliedChoiceLayout();
            if ((got == QuizScreen.ChoiceLayout.GRID) != expectGrid) {
                allOk = false;
                System.out.println("  불일치 " + q.getId() + " got=" + got + " expectGrid=" + expectGrid);
            }
            if (!expectGrid) {
                int longCount = 0;
                Font font = GameFonts.text(Font.PLAIN, 24f);
                Graphics2D g = new BufferedImage(1, 1, 1).createGraphics();
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                FontMetrics fm = g.getFontMetrics(font);
                for (String c : cq.getChoices()) {
                    if (fm.stringWidth(c) > 256 * 0.78 - 24 * 1.15) {
                        longCount++;
                    }
                }
                if (longCount == 1) {
                    onlyOneLong++;
                    check(got == QuizScreen.ChoiceLayout.WIDE, "보기 하나만 길어도 4×1 (문제 " + q.getId() + ")");
                }
            }
        }
        check(allOk, "객관식 전체: 자동 선택이 FontMetrics 독립 계산과 일치 (하나만 긴 문제 " + onlyOneLong + "개 포함)");

        // ---- 글자 크기·버튼 규격: 2×2는 24px 한 줄, 4×1은 24→20px 한 줄(모두 동일)
        edt(() -> s.showQuiz(shortQ, 1, 20));
        settle();
        boolean same = true;
        for (int i = 1; i <= 4; i++) {
            SketchButton b = (SketchButton) find(root, "choice" + i);
            same &= Math.abs(b.getFont().getSize2D() - 24f) < 0.01 && b.textLineCount() == 1 && b.getSize().equals(find(root, "choice1").getSize());
        }
        check(same, "2×2: 보기 네 개 모두 24px, 한 줄, 같은 크기");

        // ---- 연속 전환: 2×2 → 4×1 → 단답형 → 2×2 → 4×1, 클릭하면 보기 번호 그대로 제출
        Quiz inputQ = byId.apply(7);
        Quiz[] seq = {shortQ, longQ, inputQ, byId.apply(18), longQ, shortQ};
        for (Quiz q : seq) {
            edt(() -> s.showQuiz(q, 1, 20));
            settle();
            if (q instanceof ChoiceQuiz cq) {
                QuizScreen.ChoiceLayout exp = expectGrid(cq, 1.0) ? QuizScreen.ChoiceLayout.GRID : QuizScreen.ChoiceLayout.WIDE;
                String img = exp == QuizScreen.ChoiceLayout.GRID ? SketchAssets.BUTTON_SQUARE : SketchAssets.BUTTON_WIDE;
                boolean imgOk = true;
                for (int i = 1; i <= 4; i++) {
                    imgOk &= ((SketchButton) find(root, "choice" + i)).getImageFile().equals(img) && find(root, "choice" + i).isShowing();
                }
                answers.clear();
                edt(() -> ((AbstractButton) find(root, "choice3")).doClick());
                settle();
                check(imgOk && s.getAppliedChoiceLayout() == exp && answers.equals(List.of("3")),
                        "연속 전환 " + q.getId() + ": " + exp + ", 이미지 일치, 3번 클릭 → 제출 " + answers);
                check(((SketchButton) find(root, "choice3")).getText().equals(cq.getChoices().get(2)), "연속 전환 " + q.getId() + ": 선택지 순서 유지");
            } else {
                check(find(root, "inputFrame").isShowing() && (find(root, "choice1") == null || !find(root, "choice1").isShowing()) && s.getAppliedChoiceLayout() == null,
                        "연속 전환 " + q.getId() + ": 단답형 화면(입력창) 영향 없음");
            }
        }

        // ---- 창 크기 변경: 재계산, 겹침·잘림 없음, 스크롤 없음
        edt(() -> s.showQuiz(shortQ, 1, 20));
        settle();
        int[][] sizes = {{1024, 640}, {1600, 900}, {800, 900}, {1280, 720}};
        for (int[] sz : sizes) {
            resize(sz[0], sz[1]);
            double sc = Math.min(sz[0] / 1280.0, sz[1] / 720.0);
            boolean ok = true;
            for (int i = 1; i <= 4; i++) {
                SketchButton b = (SketchButton) find(root, "choice" + i);
                ok &= b.isShowing() && !b.isTextTruncated() && b.textLineCount() == 1 && rect("quizFrame").contains(rect("choice" + i));
            }
            check(ok && s.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.GRID && !barShowing(),
                    "창 " + sz[0] + "x" + sz[1] + ": 짧은 문제 2×2 유지, 잘림·스크롤 없음 (배율 " + String.format("%.2f", sc) + ")");
        }
        edt(() -> s.showQuiz(longQ, 1, 20));
        settle();
        for (int[] sz : sizes) {
            resize(sz[0], sz[1]);
            boolean ok = true;
            for (int i = 1; i <= 4; i++) {
                SketchButton b = (SketchButton) find(root, "choice" + i);
                ok &= b.isShowing() && !b.isTextTruncated() && b.textLineCount() == 1 && rect("quizFrame").contains(rect("choice" + i));
            }
            check(ok && s.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.WIDE && !barShowing(), "창 " + sz[0] + "x" + sz[1] + ": 긴 보기 4×1 유지, 잘림·스크롤 없음");
        }

        // ---- 스크롤 오류 수정: 짧은 문제(18번)는 어느 창 크기에서도 스크롤바 없음, 본문이 전부 보임
        Quiz q18 = byId.apply(18);
        for (int[] sz : new int[][] {{1280, 720}, {1264, 681}, {1024, 640}, {1600, 900}, {800, 900}, {1100, 700}}) {
            resize(sz[0], sz[1]);
            edt(() -> s.showQuiz(q18, 1, 20));
            settle();
            Rectangle body = rect("bodyScroll");
            var rt = (game.ui.components.SketchRichText) find(root, "questionArea");
            check(!barShowing() && rt.contentHeight() <= body.height, "18번(짧은 문제) " + sz[0] + "x" + sz[1] + ": 스크롤바 없음, 본문 전체 표시 (내용 " + rt.contentHeight() + " ≤ 영역 " + body.height + ")");
        }

        // ---- Q. 시작 위치: 문제 유형·길이와 관계없이 같은 y (상단 기준)
        resize(1280, 720);
        double frameY = rect("quizFrame").y;
        List<Double> tops = new ArrayList<>();
        for (int id : new int[] {8, 11, 7, 18, 3, 36, 15}) {
            Quiz q = byId.apply(id);
            edt(() -> s.showQuiz(q, 1, 20));
            settle();
            tops.add(rect("bodyScroll").y - frameY);
        }
        boolean sameTop = tops.stream().allMatch(t -> Math.abs(t - tops.get(0)) < 1.5);
        // 문제 글은 틀 모서리에서 아래로 10px 띄운다(40 → 50). 공간이 모자란 문제는 이동량을 줄이거나 원래 위치(40)를 쓴다.
        long moved = tops.stream().filter(t -> Math.abs(t - 50) < 1.5).count();
        boolean allInRange = tops.stream().allMatch(t -> t >= 39 && t <= 51);
        check(allInRange && moved >= tops.size() - 2 && Math.abs(tops.get(0) - 50) < 1.5,
                "Q. 시작 위치: 틀 위에서 " + tops + " (기본 50px = 40 + 10, 공간이 모자란 문제만 줄어듦: " + moved + "/" + tops.size() + "개 적용)");
        Quiz q36 = byId.apply(36);
        edt(() -> s.showQuiz(q36, 1, 20));
        settle();
        Rectangle b = rect("bodyScroll");
        Rectangle c1 = rect("choice1");
        check(c1.y - (b.y + b.height) <= 47, "문제와 첫 보기 사이 간격 ≤ 44px(+반올림): " + (c1.y - (b.y + b.height)));

        // ---- 배경색
        BufferedImage im = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        edt(() -> {
            Graphics2D g = im.createGraphics();
            root.paint(g);
            g.dispose();
        });
        int rgb = im.getRGB(640, 20) & 0xffffff;
        check(rgb == 0xFAFAF8, "배경색 #FAFAF8: " + Integer.toHexString(rgb));

        // ---- 오답 결과에 사용자가 고른 답 표시
        edt(() -> s.showQuiz(longQ, 1, 20));
        settle();
        edt(() -> ((AbstractButton) find(root, "choice2")).doClick());
        settle();
        edt(() -> s.showResult(QuizScreen.ResultType.WRONG, "방어 실패!", true));
        settle();
        String plain = ((game.ui.components.SketchRichText) find(root, "questionArea")).getPlainText();
        check(plain.contains("내가 고른 답 : 2. " + ((ChoiceQuiz) longQ).getChoices().get(1)) && plain.contains("정답 : "), "오답 결과: 내가 고른 답 표시 → " + plain.lines().findFirst().orElse("") + " / " + plain.lines().skip(1).findFirst().orElse(""));
        edt(() -> s.showQuiz(inputQ, 1, 20));
        settle();
        edt(() -> {
            ((JTextField) find(root, "inputField")).setText("abc");
            ((JTextField) find(root, "inputField")).postActionEvent();
        });
        settle();
        edt(() -> s.showResult(QuizScreen.ResultType.WRONG, "방어 실패!", true));
        settle();
        plain = ((game.ui.components.SketchRichText) find(root, "questionArea")).getPlainText();
        check(plain.contains("내가 쓴 답 : abc"), "단답형 오답 결과: 내가 쓴 답 표시");
        edt(() -> s.showQuiz(shortQ, 1, 20));
        settle();
        edt(() -> s.showResult(QuizScreen.ResultType.CORRECT, "반격 성공!", true));
        settle();
        plain = ((game.ui.components.SketchRichText) find(root, "questionArea")).getPlainText();
        check(!plain.contains("내가 고른 답"), "정답 결과에는 '내가 고른 답'을 보이지 않음");
        edt(() -> s.showQuiz(shortQ, 1, 20));
        settle();
        edt(() -> s.showResult(QuizScreen.ResultType.TIMEOUT, "방어 실패!", true));
        settle();
        plain = ((game.ui.components.SketchRichText) find(root, "questionArea")).getPlainText();
        check(plain.contains("내가 고른 답 : 제출 없음 (시간 초과)"), "시간 초과 결과: 제출한 답이 없음을 표시");
        Rectangle div = rect("divider");
        Rectangle bodyR = rect("bodyScroll");
        check(bodyR.y - (div.y + div.height) >= 18, "구분선과 정답 사이 여백 " + (bodyR.y - (div.y + div.height)) + "px");

        // ---- 팝업: 안내 문구와 버튼 사이 간격, GAME OVER 타이머 숨김
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            edt(() -> {
                s.resetBattle();
                s.updateHp(1, 3, 3, 3);
                s.showQuiz(shortQ, 1, 20);
                if (kk == 0) {
                    s.showLowHpWarning("체력이 1 남았습니다. 회복 아이템을 사용하면 HP가 1 회복됩니다.", true);
                } else if (kk == 1) {
                    s.openPause();
                } else {
                    s.showGameOver("체력이 모두 소진되었습니다.");
                }
            });
            settle();
            Rectangle msg = rect("popupMessage");
            Rectangle btn = rect("popupPrimary");
            Rectangle frame = rect("quizFrame");
            Rectangle title = rect("popupTitle");
            String label = new String[] {"HP 경고", "일시정지", "GAME OVER"}[k];
            check(btn.y - (msg.y + msg.height) <= 40 && frame.contains(title) && frame.contains(btn), label + ": 안내 문구-버튼 간격 " + (btn.y - (msg.y + msg.height)) + "px (≤40), 틀 안");
            if (k == 2) {
                check(!find(root, "timerLabel").isShowing() && !find(root, "timerLine").isShowing() && !find(root, "fire").isShowing(), "GAME OVER: TIME·타이머 선·불꽃 모두 숨김");
            } else {
                check(find(root, "timerLine").isShowing() && find(root, "timerLabel").isShowing(), label + ": 타이머 선·문구 표시 (" + s.timerText() + ")");
            }
        }

        // 결과 화면 위에 HP 경고가 뜬 경우(실제 전투 흐름)에도 제목 → 구분선 → 문구 → 버튼 순서
        edt(() -> {
            s.resetBattle();
            s.showQuiz(shortQ, 1, 20);
            s.showResult(QuizScreen.ResultType.WRONG, "방어 실패!", true);
            s.showLowHpWarning("체력이 1 남았습니다. 회복 아이템을 사용하면 HP가 1 회복됩니다.", true);
        });
        settle();
        Rectangle pt = rect("popupTitle");
        Rectangle pd = rect("divider");
        Rectangle pm = rect("popupMessage");
        Rectangle pb = rect("popupPrimary");
        check(pt.y + pt.height <= pd.y + 2 && pd.y + pd.height <= pm.y + 2 && pm.y + pm.height <= pb.y + 2, "결과 위 HP 경고: 제목 < 구분선 < 문구 < 버튼 순서 (" + pt.y + "," + pd.y + "," + pm.y + "," + pb.y + ")");

        // ---- 캐릭터 위치: 주인공·골렘은 같은 발 밑 기준선(660), 유령은 공중(더 위)
        Rectangle mv = rect("monsterView");
        Rectangle pv = rect("playerView");
        Rectangle mh = rect("monsterHearts");
        check(Math.abs(mv.y + mv.height - 590) <= 2 && mv.y >= mh.y + mh.height, "골렘 영역 아래 끝 y=" + (mv.y + mv.height) + " (≈590), HP 줄 아래 " + (mh.y + mh.height));
        check(pv.y + pv.height == 660, "주인공 영역 아래 끝 y=" + (pv.y + pv.height));

        // ---- quiz.txt 전체 문제 배치 (자동 레이아웃): 1280x720, 1264x681(실제 게임 창), 1024x640, 800x900
        int[][] fullSizes = {{1280, 720}, {1264, 681}, {1024, 640}, {800, 900}};
        for (int[] fs : fullSizes) {
        resize(fs[0], fs[1]);
        System.out.println("== 전체 문제 " + fs[0] + "x" + fs[1]);
        int grid = 0, wide = 0, input = 0, needsData = 0;
        List<String> scroll = new ArrayList<>();
        List<String> wrapped = new ArrayList<>();
        boolean allClean = true;
        for (Quiz q : all) {
            edt(() -> s.showQuiz(q, 1, 20));
            settle();
            QuizScreen.QuestionPlan p = s.getQuestionPlan();
            Rectangle frame = rect("quizFrame");
            Rectangle body = rect("bodyScroll");
            JScrollPane sp = (JScrollPane) find(root, "bodyScroll");
            boolean bar = sp.getVerticalScrollBar().isShowing();
            boolean consistent = bar == (p.status() == QuizScreen.FitStatus.SCROLLS);
            boolean inside = frame.contains(body);
            boolean fullyShown = bar || ((game.ui.components.SketchRichText) find(root, "questionArea")).contentHeight() <= body.height + 1;
            if (q instanceof ChoiceQuiz) {
                if (s.getAppliedChoiceLayout() == QuizScreen.ChoiceLayout.GRID) {
                    grid++;
                } else {
                    wide++;
                }
                float size = -1;
                for (int i = 1; i <= 4; i++) {
                    SketchButton cb = (SketchButton) find(root, "choice" + i);
                    inside &= frame.contains(rect("choice" + i)) && !rect("choice" + i).intersects(body);
                    consistent &= !cb.isTextTruncated();
                    if (size < 0) {
                        size = cb.fittedFont().getSize2D();
                    }
                    consistent &= Math.abs(size - cb.fittedFont().getSize2D()) < 0.01 && Math.abs(cb.getHeight() - find(root, "choice1").getHeight()) <= 1 && Math.abs(cb.getWidth() - find(root, "choice1").getWidth()) <= 1;
                }
            } else {
                input++;
                inside &= frame.contains(rect("inputFrame")) && frame.contains(rect("inputSubmitButton")) && !rect("inputFrame").intersects(body);
            }
            if (!(consistent && inside && fullyShown)) {
                allClean = false;
                System.out.println("  문제 " + q.getId() + ": 배치 오류 consistent=" + consistent + " inside=" + inside + " fullyShown=" + fullyShown);
            }
            if (p.status() == QuizScreen.FitStatus.SCROLLS) {
                scroll.add(q.getId() + "(부족 " + Math.round(p.textH() - p.regionH()) + "px)");
                needsData++;
            } else if (p.status() == QuizScreen.FitStatus.CODE_WRAPPED) {
                wrapped.add(String.valueOf(q.getId()));
                needsData++;
            }
        }
        check(allClean, fs[0] + "x" + fs[1] + " 전체 " + all.size() + "문제: 글자 잘림·겹침·안전영역 이탈·스크롤바 불일치·보기 규격 불일치 없음");
        check(grid + wide + input == all.size(), fs[0] + "x" + fs[1] + " 자동 배치 개수: 2×2=" + grid + ", 4×1=" + wide + ", 단답형=" + input);
        check(scroll.isEmpty(), fs[0] + "x" + fs[1] + " 스크롤이 필요한 문제 없음 " + scroll);
        System.out.println("INFO 코드 줄바꿈이 필요한 문제: " + wrapped);
        }

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        System.exit(fails);
    }
}
