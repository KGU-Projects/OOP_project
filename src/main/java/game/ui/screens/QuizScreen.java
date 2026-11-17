package game.ui.screens;

import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.BaseScreen;
import game.ui.ScreenManager;
import game.ui.components.GameButton;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 퀴즈 전투 화면. 화면 표시와 사용자 입력만 맡는다.
 *
 * 정답 판정, HP 계산, 다음 문제 선택 같은 전투 규칙은 이 화면이 아니라 {@link QuizListener}를 구현한 쪽
 * (지금은 {@link QuizScreenDemo}, 나중에는 실제 게임 로직)이 맡는다.
 *
 * 흐름: showQuiz → (사용자 제출 또는 시간 초과) → 리스너가 판정 → showResult → '다음 문제' 클릭 → 리스너가 showQuiz
 * 또는 전투가 끝나면 showBattleEnd → '돌아가기'(데모: '다시 플레이') 클릭 → onReturnRequested.
 */
public class QuizScreen extends BaseScreen {

    /** 화면에서 일어난 사용자 행동을 전투 로직에 알린다. 모든 호출은 EDT에서 일어난다. */
    public interface QuizListener {
        /** 답안 제출. 객관식은 보기 번호(1부터) 문자열, 단답형은 입력한 문자열이다. */
        void onAnswerSubmitted(Quiz quiz, String answer);

        /** 제한시간이 다 지났다. */
        void onTimeExpired(Quiz quiz);

        /** 결과 확인 후 '다음 문제' 버튼을 눌렀다. */
        void onNextRequested();

        /**
         * 전투가 끝난 뒤 복귀 버튼을 눌렀다. 실제 게임에서는 맵/스테이지 화면으로 돌아가고,
         * 독립 데모에서는 전투를 처음부터 다시 시작한다. 버튼은 한 번만 눌린다.
         */
        default void onReturnRequested() {
        }

        /**
         * 팝업(HP 경고, 일시정지, GAME OVER)에서 고른 선택. 팝업 하나당 한 번만 호출된다.
         * 화면은 타이머·입력만 처리하고, 선택에 따른 게임 진행(재시작, 화면 이동 등)은 받는 쪽이 정한다.
         */
        default void onPopupChoice(PopupChoice choice) {
        }
    }

    /** 한 문제의 결과 종류 */
    public enum ResultType {
        CORRECT("정답입니다!", new Color(70, 200, 120)),
        WRONG("오답입니다!", new Color(235, 90, 90)),
        TIMEOUT("시간 초과!", new Color(240, 160, 60));

        private final String title;
        private final Color color;

        ResultType(String title, Color color) {
            this.title = title;
            this.color = color;
        }
    }

    /** 전투 화면 위에 뜨는 팝업 종류 */
    public enum PopupType {
        /** 플레이어 HP가 1이 되었을 때 경고 */
        LOW_HP,
        /** 일시정지 */
        PAUSE,
        /** HP 0 */
        GAME_OVER
    }

    /** 팝업에서 고를 수 있는 선택 */
    public enum PopupChoice {
        /** HP 경고: 계속 싸우기 */
        LOW_HP_CONTINUE(PopupType.LOW_HP, true),
        /** HP 경고: 체력 보충하러 가기 */
        LOW_HP_RECOVER(PopupType.LOW_HP, false),
        /** 일시정지: 계속하기 */
        PAUSE_RESUME(PopupType.PAUSE, true),
        /** 일시정지: 전투 중단 */
        PAUSE_ABORT(PopupType.PAUSE, false),
        /** GAME OVER: 현재 스테이지 재시작 */
        GAME_OVER_RESTART(PopupType.GAME_OVER, false),
        /** GAME OVER: 스테이지 선택으로 돌아가기 */
        GAME_OVER_STAGE_SELECT(PopupType.GAME_OVER, false);

        private final PopupType popup;
        private final boolean continuesBattle;

        PopupChoice(PopupType popup, boolean continuesBattle) {
            this.popup = popup;
            this.continuesBattle = continuesBattle;
        }

        public PopupType popup() {
            return popup;
        }

        /** true면 전투를 이어 가고, false면 이 화면을 떠나는 선택이다 */
        public boolean continuesBattle() {
            return continuesBattle;
        }
    }

    /** 화면의 진행 단계 */
    public enum Phase {
        /** 아직 문제가 없다 */
        READY,
        /** 답을 입력받는 중 (타이머 동작) */
        ANSWERING,
        /** 답을 제출했거나 시간이 끝나 결과를 기다리는 중 (입력 잠금) */
        WAITING_RESULT,
        /** 결과와 해설을 보여 주는 중 */
        SHOWING_RESULT,
        /** 전투 종료. 더 이상 입력과 진행을 받지 않는다 */
        FINISHED
    }

    // 색상
    private static final Color BG = new Color(28, 22, 52);
    private static final Color CARD_BG = new Color(246, 244, 255);
    private static final Color CARD_BORDER = new Color(120, 100, 200);
    private static final Color TEXT_DARK = new Color(35, 30, 60);
    private static final Color CODE_BG = new Color(32, 30, 48);
    private static final Color CODE_FG = new Color(225, 228, 245);
    private static final Color CHOICE_BG = Color.WHITE;
    private static final Color CHOICE_SELECTED = new Color(205, 195, 255);
    private static final Color CHOICE_CORRECT = new Color(185, 240, 200);
    private static final Color CHOICE_WRONG = new Color(255, 195, 195);
    private static final Color TIMER_NORMAL = new Color(240, 240, 255);
    private static final Color TIMER_WARNING = new Color(255, 110, 110);

    private static final int TIMER_TICK_MS = 100;
    private static final int WARNING_SECONDS = 5;
    /** showQuiz의 제한시간으로 쓰면 타이머 없이 문제를 낸다 (시간 초과 규칙이 없는 게임 모드용). */
    public static final int NO_TIME_LIMIT = 0;
    /** 최대 HP가 이보다 크면 하트 대신 숫자와 막대로만 표시한다 (몬스터 HP가 큰 경우). */
    private static final int MAX_HEART_ICONS = 8;
    /** 문제 문장은 이 줄 수까지 보이고 넘치면 스크롤 */
    private static final int QUESTION_MAX_LINES = 4;
    /** 결과·해설은 이 줄 수까지 보이고 넘치면 스크롤 */
    private static final int RESULT_MAX_LINES = 5;
    private static final String DEFAULT_RETURN_TEXT = "돌아가기";

    private QuizListener listener;
    private Phase phase = Phase.READY;
    private Quiz currentQuiz;

    // 타이머
    private final Timer countdown;
    private int timeLimitSeconds;
    private long deadlineNanos;
    private long pausedRemainingMillis = -1;

    // 상단
    private final JLabel stageLabel = new JLabel(" ");
    private final JLabel timerLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar timeBar = new JProgressBar(0, 1000);

    // 좌우 캐릭터
    private final CombatantView playerView = new CombatantView("사용자", true);
    private final CombatantView monsterView = new CombatantView("몬스터", false);

    // 문제 카드
    private final JTextArea questionArea = createWrappingText(new Font("SansSerif", Font.BOLD, 19), TEXT_DARK);
    private final JScrollPane questionScroll = createCappedScroll(questionArea, QUESTION_MAX_LINES);
    private final JTextArea codeArea = new JTextArea();
    private final JScrollPane codeScroll = new JScrollPane(codeArea);
    private final JPanel filler = new JPanel();

    private final JPanel answerPanel = new JPanel(new BorderLayout());
    private JPanel choiceArea;
    private JPanel inputArea;
    private final JPanel choicePanel = new JPanel(new GridLayout(0, 2, 10, 10));
    private final ButtonGroup choiceGroup = new ButtonGroup();
    private final List<JToggleButton> choiceButtons = new ArrayList<>();
    private final GameButton choiceSubmitButton;
    private final JTextField inputField = new JTextField();
    private final GameButton inputSubmitButton;

    private final JPanel resultPanel = new JPanel(new BorderLayout(0, 6));
    private final JLabel resultTitle = new JLabel(" ");
    private final JTextArea resultDetail = createWrappingText(new Font("SansSerif", Font.PLAIN, 15), TEXT_DARK);
    private final JScrollPane resultScroll = createCappedScroll(resultDetail, RESULT_MAX_LINES);
    private final JLabel endLabel = new JLabel(" ", SwingConstants.CENTER);
    private final GameButton nextButton;
    private final GameButton returnButton;

    // 일시정지 버튼과 팝업 (팝업은 문제 카드 자리에 뜬다: 일시정지 중 문제를 볼 수 없게)
    private final GameButton pauseButton;
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerLayout);
    private final JPanel popupBox = new JPanel(new BorderLayout(0, 14));
    private final JLabel popupTitle = new JLabel(" ", SwingConstants.CENTER);
    private final JTextArea popupMessage = createWrappingText(new Font("SansSerif", Font.PLAIN, 17), Color.WHITE);
    private final GameButton popupPrimary;
    private final GameButton popupSecondary;
    private PopupType popup;
    private PopupChoice popupPrimaryChoice;
    private PopupChoice popupSecondaryChoice;

    public QuizScreen(ScreenManager screenManager) {
        super(screenManager, new BorderLayout(18, 12));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(16, 20, 20, 20));

        choiceSubmitButton = new GameButton("제출", this::submitSelectedChoice);
        inputSubmitButton = new GameButton("제출", this::submitInput);
        nextButton = new GameButton("다음 문제", this::requestNext);
        returnButton = new GameButton(DEFAULT_RETURN_TEXT, this::requestReturn);
        pauseButton = new GameButton("일시정지 (ESC)", this::requestPause);
        popupPrimary = new GameButton(" ", () -> choose(popupPrimaryChoice));
        popupSecondary = new GameButton(" ", () -> choose(popupSecondaryChoice));

        countdown = new Timer(TIMER_TICK_MS, e -> onTimerTick());

        panel.add(createTopBar(), BorderLayout.NORTH);
        panel.add(wrapSide(playerView), BorderLayout.WEST);
        panel.add(wrapSide(monsterView), BorderLayout.EAST);
        centerPanel.setOpaque(false);
        centerPanel.add(createQuestionCard(), "CARD");
        centerPanel.add(createPopupPanel(), "POPUP");
        panel.add(centerPanel, BorderLayout.CENTER);

        setupKeyBindings();
        resetQuestionView();
    }

    // ---------------------------------------------------------------------
    // 전투 로직이 호출하는 API
    // ---------------------------------------------------------------------

    public void setQuizListener(QuizListener listener) {
        this.listener = listener;
    }

    /** 좌우 캐릭터 이름 표시 */
    public void setCombatantNames(String playerName, String monsterName) {
        playerView.setDisplayName(playerName);
        monsterView.setDisplayName(monsterName);
    }

    /** HP 표시 갱신 */
    public void updateHp(int playerHp, int playerMaxHp, int monsterHp, int monsterMaxHp) {
        playerView.setHp(playerHp, playerMaxHp);
        monsterView.setHp(monsterHp, monsterMaxHp);
    }

    /**
     * 새 문제를 표시하고 입력 상태와 타이머를 초기화한다. 전투가 끝난 뒤에는 무시한다.
     *
     * @param questionNo       화면에 표시할 문제 순번 (1부터)
     * @param timeLimitSeconds 제한시간(초). {@link #NO_TIME_LIMIT}(0)이면 타이머 없이 낸다
     */
    public void showQuiz(Quiz quiz, int questionNo, int timeLimitSeconds) {
        if (quiz == null) {
            throw new IllegalArgumentException("quiz is null");
        }
        if (timeLimitSeconds < 0) {
            throw new IllegalArgumentException("timeLimitSeconds must be 0 (no limit) or positive: " + timeLimitSeconds);
        }
        if (phase == Phase.FINISHED) {
            return;
        }
        this.currentQuiz = quiz;
        this.timeLimitSeconds = timeLimitSeconds;

        closePopup();
        resetQuestionView();
        stageLabel.setText("문제 " + questionNo);

        QuestionParts parts = splitQuestion(quiz.getQuestion());
        questionArea.setText("Q. " + parts.prompt());
        questionArea.setCaretPosition(0);
        boolean hasCode = !parts.code().isEmpty();
        codeArea.setText(parts.code());
        codeArea.setCaretPosition(0);
        codeScroll.setVisible(hasCode);
        filler.setVisible(!hasCode);

        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            buildChoices(choiceQuiz.getChoices());
            showAnswerArea(choiceArea);
        } else {
            showAnswerArea(inputArea);
        }

        phase = Phase.ANSWERING;
        setInputsEnabled(true);
        startCountdown();
        updatePauseButton();

        refreshLayout();
        SwingUtilities.invokeLater(() -> {
            refreshLayout(); // 줄바꿈 높이는 폭이 정해진 뒤에 계산되므로 한 번 더 배치
            if (currentQuiz instanceof ChoiceQuiz) {
                panel.requestFocusInWindow();
            } else {
                inputField.requestFocusInWindow();
            }
        });
    }

    /**
     * 판정 결과와 해설을 표시한다. 입력과 타이머는 이미 멈춘 상태다.
     *
     * @param detail   결과 아래에 덧붙일 문장 (예: "몬스터 HP -1"). null 가능
     * @param showNext '다음 문제' 버튼을 보일지 여부. 전투가 끝났으면 false
     */
    public void showResult(ResultType type, String detail, boolean showNext) {
        if (currentQuiz == null || phase == Phase.FINISHED) {
            return;
        }
        stopCountdown();
        setInputsEnabled(false);
        highlightChoices();

        resultTitle.setText(type.title);
        resultTitle.setForeground(type.color);
        resultPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(type.color, 2, true), new EmptyBorder(8, 12, 8, 12)));

        StringBuilder sb = new StringBuilder();
        if (detail != null && !detail.isBlank()) {
            sb.append(detail).append('\n');
        }
        if (type != ResultType.CORRECT) {
            sb.append("정답: ").append(correctAnswerText(currentQuiz)).append('\n');
        }
        String explanation = currentQuiz.getExplanation();
        sb.append("해설: ").append(explanation.isBlank() ? "(해설 없음)" : explanation);
        resultDetail.setText(sb.toString());
        resultDetail.setCaretPosition(0);
        resultPanel.setVisible(true);

        nextButton.setVisible(showNext);
        nextButton.setEnabled(showNext);
        phase = Phase.SHOWING_RESULT;
        updatePauseButton();
        refreshLayout();
        SwingUtilities.invokeLater(this::refreshLayout);
        if (showNext) {
            SwingUtilities.invokeLater(nextButton::requestFocusInWindow);
        }
    }

    /** 전투 종료 표시. 복귀 버튼 문구는 '돌아가기'. */
    public void showBattleEnd(boolean victory, String message) {
        showBattleEnd(victory, message, DEFAULT_RETURN_TEXT);
    }

    /**
     * 전투 종료 표시. 이후 답안 제출과 다음 문제 진행을 모두 막고 복귀 버튼만 보여 준다.
     *
     * @param returnButtonText 복귀 버튼 문구 (예: "돌아가기", "다시 플레이")
     */
    public void showBattleEnd(boolean victory, String message, String returnButtonText) {
        stopCountdown();
        setInputsEnabled(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        endLabel.setText((victory ? "승리! " : "패배... ") + (message == null ? "" : message));
        endLabel.setForeground(victory ? new Color(255, 215, 90) : new Color(255, 120, 120));
        endLabel.setVisible(true);
        stageLabel.setText(victory ? "전투 승리" : "전투 패배");
        returnButton.setText(returnButtonText == null || returnButtonText.isBlank()
                ? DEFAULT_RETURN_TEXT : returnButtonText);
        returnButton.setVisible(true);
        returnButton.setEnabled(true);
        phase = Phase.FINISHED;
        updatePauseButton();
        refreshLayout();
        SwingUtilities.invokeLater(returnButton::requestFocusInWindow);
    }

    /**
     * HP 경고 팝업 (계속 싸우기 / 체력 보충하러 가기). 답을 입력하는 중이면 타이머를 멈추고,
     * '계속 싸우기'를 고르면 남은 시간부터 이어 간다. 이미 다른 팝업이 떠 있거나 전투가 끝났으면 무시한다.
     */
    public void showLowHpWarning(String message) {
        if (popup != null || phase == Phase.FINISHED || phase == Phase.READY) {
            return;
        }
        showPopup(PopupType.LOW_HP, "체력 경고!", message, new Color(245, 170, 60),
                PopupChoice.LOW_HP_CONTINUE, "계속 싸우기", PopupChoice.LOW_HP_RECOVER, "체력 보충하러 가기");
    }

    /**
     * GAME OVER 팝업 (현재 스테이지 재시작 / 스테이지 선택으로 돌아가기). 전투를 끝내고 모든 입력을 막는다.
     * 결과·해설은 팝업 뒤 카드에 남아 있다.
     */
    public void showGameOver(String message) {
        stopCountdown();
        setInputsEnabled(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        returnButton.setVisible(false);
        returnButton.setEnabled(false);
        endLabel.setText("GAME OVER" + (message == null || message.isBlank() ? "" : " - " + message));
        endLabel.setForeground(new Color(255, 120, 120));
        endLabel.setVisible(true);
        stageLabel.setText("GAME OVER");
        phase = Phase.FINISHED;
        closePopup();
        showPopup(PopupType.GAME_OVER, "GAME OVER", message, new Color(240, 90, 100),
                PopupChoice.GAME_OVER_RESTART, "현재 스테이지 재시작",
                PopupChoice.GAME_OVER_STAGE_SELECT, "스테이지 선택으로");
    }

    /** 일시정지 팝업을 연다 (일시정지 버튼·ESC와 같다). 답을 입력하는 중이거나 결과를 보는 중에만 열린다. */
    public void openPause() {
        requestPause();
    }

    private void requestPause() {
        if (popup != null || (phase != Phase.ANSWERING && phase != Phase.SHOWING_RESULT)) {
            return;
        }
        showPopup(PopupType.PAUSE, "일시정지", "타이머가 멈췄습니다. 전투를 중단하면 이 화면을 떠납니다.",
                new Color(150, 130, 255),
                PopupChoice.PAUSE_RESUME, "계속하기", PopupChoice.PAUSE_ABORT, "전투 중단");
    }

    /** 지금 떠 있는 팝업. 없으면 null */
    public PopupType getPopup() {
        return popup;
    }

    /** 새 전투를 시작할 수 있도록 화면을 처음 상태로 되돌린다. */
    public void resetBattle() {
        stopCountdown();
        closePopup();
        currentQuiz = null;
        phase = Phase.READY;
        resetQuestionView();
        questionArea.setText(" ");
        codeArea.setText("");
        codeScroll.setVisible(false);
        filler.setVisible(true);
        stageLabel.setText(" ");
        timerLabel.setText(" ");
        timerLabel.setForeground(TIMER_NORMAL);
        timeBar.setValue(0);
        updatePauseButton();
        refreshLayout();
    }

    public Phase getPhase() {
        return phase;
    }

    public Quiz getCurrentQuiz() {
        return currentQuiz;
    }

    /** 남은 시간(초, 올림). 타이머가 없거나 제한시간 없는 문제면 0 */
    public int getRemainingSeconds() {
        return (int) Math.ceil(remainingMillis() / 1000.0);
    }

    // ---------------------------------------------------------------------
    // Screen 생명주기: 다른 화면으로 나가면 타이머를 멈추고, 돌아오면 남은 시간부터 이어간다.
    // ---------------------------------------------------------------------

    @Override
    public void onEnter() {
        if (popup == null) { // 팝업(일시정지 등)이 떠 있으면 고를 때까지 멈춘 채로 둔다
            resumeCountdown();
        }
    }

    @Override
    public void onExit() {
        pauseCountdown();
    }

    // ---------------------------------------------------------------------
    // 입력 처리
    // ---------------------------------------------------------------------

    private void submitSelectedChoice() {
        for (int i = 0; i < choiceButtons.size(); i++) {
            if (choiceButtons.get(i).isSelected()) {
                submit(String.valueOf(i + 1));
                return;
            }
        }
    }

    private void submitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) {
            inputField.requestFocusInWindow();
            return; // 빈 답은 제출하지 않는다
        }
        submit(text);
    }

    /** 답안 제출. ANSWERING 단계에서 한 번만 리스너로 전달된다 (중복 제출 방지). */
    private void submit(String answer) {
        if (phase != Phase.ANSWERING || currentQuiz == null || popup != null) {
            return;
        }
        lockForJudging();
        if (listener != null) {
            listener.onAnswerSubmitted(currentQuiz, answer);
        }
    }

    private void requestNext() {
        if (phase != Phase.SHOWING_RESULT || popup != null) {
            return;
        }
        phase = Phase.WAITING_RESULT;
        nextButton.setEnabled(false);
        updatePauseButton();
        if (listener != null) {
            listener.onNextRequested();
        }
    }

    /** 전투 종료 후 복귀 버튼. FINISHED 단계에서 한 번만 리스너로 전달된다. */
    private void requestReturn() {
        if (phase != Phase.FINISHED || !returnButton.isEnabled() || popup != null) {
            return;
        }
        returnButton.setEnabled(false);
        if (listener != null) {
            listener.onReturnRequested();
        }
    }

    private void lockForJudging() {
        phase = Phase.WAITING_RESULT;
        stopCountdown();
        setInputsEnabled(false);
        updatePauseButton();
    }

    // ---------------------------------------------------------------------
    // 팝업
    // ---------------------------------------------------------------------

    private void showPopup(PopupType type, String title, String message, Color accent,
                           PopupChoice primary, String primaryText, PopupChoice secondary, String secondaryText) {
        pauseCountdown();
        popup = type;
        popupPrimaryChoice = primary;
        popupSecondaryChoice = secondary;
        popupTitle.setText(title);
        popupTitle.setForeground(accent);
        popupMessage.setText(message == null ? "" : message);
        popupBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accent, 3, true), new EmptyBorder(22, 26, 22, 26)));
        popupPrimary.setText(primaryText);
        popupSecondary.setText(secondaryText);
        popupPrimary.setEnabled(true);
        popupSecondary.setEnabled(true);
        centerLayout.show(centerPanel, "POPUP");
        updatePauseButton();
        refreshLayout();
        SwingUtilities.invokeLater(popupPrimary::requestFocusInWindow);
    }

    private void closePopup() {
        popup = null;
        popupPrimaryChoice = null;
        popupSecondaryChoice = null;
        centerLayout.show(centerPanel, "CARD");
    }

    /** 팝업 버튼 선택. 지금 떠 있는 팝업의 선택만, 한 번만 받는다. */
    private void choose(PopupChoice choice) {
        if (choice == null || popup != choice.popup()) {
            return;
        }
        popupPrimary.setEnabled(false);
        popupSecondary.setEnabled(false);
        closePopup();
        if (choice.continuesBattle()) {
            resumeCountdown();
            updatePauseButton();
            if (phase == Phase.ANSWERING && currentQuiz instanceof ChoiceQuiz) {
                SwingUtilities.invokeLater(panel::requestFocusInWindow);
            } else if (phase == Phase.ANSWERING) {
                SwingUtilities.invokeLater(inputField::requestFocusInWindow);
            } else if (phase == Phase.SHOWING_RESULT && nextButton.isVisible()) {
                SwingUtilities.invokeLater(nextButton::requestFocusInWindow);
            }
        } else {
            leaveBattle(choice);
        }
        refreshLayout();
        if (listener != null) {
            listener.onPopupChoice(choice);
        }
    }

    /** 화면을 떠나는 선택 뒤: 더 이상 입력·진행을 받지 않는다. 실제 이동은 리스너가 한다. */
    private void leaveBattle(PopupChoice choice) {
        stopCountdown();
        setInputsEnabled(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        returnButton.setVisible(false);
        returnButton.setEnabled(false);
        String text = switch (choice) {
            case PAUSE_ABORT -> "전투를 중단했습니다.";
            case LOW_HP_RECOVER -> "체력을 보충하러 갑니다.";
            case GAME_OVER_RESTART -> "스테이지를 다시 시작합니다.";
            case GAME_OVER_STAGE_SELECT -> "스테이지 선택으로 돌아갑니다.";
            default -> "";
        };
        endLabel.setText(text);
        endLabel.setForeground(new Color(220, 210, 255));
        endLabel.setVisible(true);
        phase = Phase.FINISHED;
        updatePauseButton();
    }

    private void updatePauseButton() {
        boolean available = popup == null && (phase == Phase.ANSWERING || phase == Phase.SHOWING_RESULT);
        pauseButton.setVisible(available);
        pauseButton.setEnabled(available);
    }

    private void setInputsEnabled(boolean enabled) {
        for (JToggleButton b : choiceButtons) {
            b.setEnabled(enabled);
        }
        inputField.setEnabled(enabled);
        inputField.setEditable(enabled);
        inputSubmitButton.setEnabled(enabled);
        choiceSubmitButton.setEnabled(enabled && choiceGroup.getSelection() != null);
    }

    private void setupKeyBindings() {
        // 객관식: 숫자키로 보기 선택, Enter로 제출
        for (int n = 1; n <= 9; n++) {
            int choiceNo = n;
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, 0), "choice_" + n, () -> selectChoiceByKey(choiceNo));
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + n, 0), "choice_np_" + n,
                    () -> selectChoiceByKey(choiceNo));
        }
        // ESC: 일시정지 열기 / 일시정지 중이면 계속하기
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "pause_toggle", () -> {
            if (popup == PopupType.PAUSE) {
                choose(PopupChoice.PAUSE_RESUME);
            } else {
                requestPause();
            }
        });
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit_or_next", () -> {
            if (popup != null) {
                return; // 팝업 버튼은 클릭(또는 포커스 후 Space)으로만 고른다
            }
            if (phase == Phase.ANSWERING && currentQuiz instanceof ChoiceQuiz) {
                submitSelectedChoice();
            } else if (phase == Phase.SHOWING_RESULT && nextButton.isVisible()) {
                requestNext();
            }
        });
    }

    private void selectChoiceByKey(int choiceNo) {
        if (phase != Phase.ANSWERING || !(currentQuiz instanceof ChoiceQuiz) || popup != null) {
            return;
        }
        if (inputField.isFocusOwner()) {
            return;
        }
        if (choiceNo >= 1 && choiceNo <= choiceButtons.size()) {
            choiceButtons.get(choiceNo - 1).setSelected(true);
            onChoiceSelectionChanged();
        }
    }

    private void bindKey(KeyStroke stroke, String name, Runnable action) {
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        panel.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------------
    // 타이머
    // ---------------------------------------------------------------------

    private void startCountdown() {
        pausedRemainingMillis = -1;
        if (timeLimitSeconds == NO_TIME_LIMIT) {
            countdown.stop();
            timerLabel.setText("제한 시간 없음");
            timerLabel.setForeground(TIMER_NORMAL);
            timeBar.setForeground(new Color(140, 120, 255));
            timeBar.setValue(1000);
            return;
        }
        deadlineNanos = System.nanoTime() + timeLimitSeconds * 1_000_000_000L;
        updateTimerDisplay(timeLimitSeconds * 1000L);
        countdown.restart();
    }

    private void stopCountdown() {
        countdown.stop();
        pausedRemainingMillis = -1;
    }

    /** 남은 시간을 기억하고 타이머를 멈춘다 (일시정지·팝업·화면 전환). 이미 멈춰 있으면 그대로. */
    private void pauseCountdown() {
        if (phase == Phase.ANSWERING && countdown.isRunning()) {
            long remaining = remainingMillis();
            countdown.stop();
            pausedRemainingMillis = remaining;
            updateTimerDisplay(remaining);
        }
    }

    /** 멈춘 시점의 남은 시간부터 다시 센다. */
    private void resumeCountdown() {
        if (phase == Phase.ANSWERING && pausedRemainingMillis >= 0 && timeLimitSeconds != NO_TIME_LIMIT) {
            deadlineNanos = System.nanoTime() + pausedRemainingMillis * 1_000_000L;
            pausedRemainingMillis = -1;
            countdown.start();
        }
    }

    private long remainingMillis() {
        if (phase != Phase.ANSWERING || timeLimitSeconds == NO_TIME_LIMIT) {
            return 0;
        }
        if (pausedRemainingMillis >= 0) {
            return pausedRemainingMillis;
        }
        return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000L);
    }

    private void onTimerTick() {
        if (phase != Phase.ANSWERING) {
            countdown.stop();
            return;
        }
        long remaining = remainingMillis();
        updateTimerDisplay(remaining);
        if (remaining <= 0) {
            onTimeUp();
        }
    }

    /**
     * 제한시간이 끝났을 때: 고른 보기나 입력한 답이 있으면 그 답을 자동 제출해 정답·오답으로 판정받고,
     * 답이 없을 때만 시간 초과로 알린다. 수동 제출과 같은 submit 경로를 쓰므로 판정은 한 번만 일어난다.
     * 팝업(일시정지 등) 중에는 타이머가 멈춰 있어 여기 오지 않는다.
     */
    private void onTimeUp() {
        if (phase != Phase.ANSWERING || currentQuiz == null || popup != null) {
            return;
        }
        String pending = pendingAnswer();
        if (pending != null) {
            submit(pending);
            return;
        }
        Quiz expired = currentQuiz;
        lockForJudging();
        if (listener != null) {
            listener.onTimeExpired(expired);
        }
    }

    /** 지금 화면에 들어 있는 답. 객관식은 선택한 보기 번호, 단답형은 입력한 내용. 없으면 null */
    private String pendingAnswer() {
        if (currentQuiz instanceof ChoiceQuiz) {
            for (int i = 0; i < choiceButtons.size(); i++) {
                if (choiceButtons.get(i).isSelected()) {
                    return String.valueOf(i + 1);
                }
            }
            return null;
        }
        String text = inputField.getText();
        return text == null || text.isBlank() ? null : text;
    }

    private void updateTimerDisplay(long remainingMillis) {
        int seconds = (int) Math.ceil(remainingMillis / 1000.0);
        timerLabel.setText(String.format("남은 시간  00:%02d", seconds));
        boolean warning = seconds <= WARNING_SECONDS;
        timerLabel.setForeground(warning ? TIMER_WARNING : TIMER_NORMAL);
        timeBar.setForeground(warning ? TIMER_WARNING : new Color(140, 120, 255));
        int value = timeLimitSeconds <= 0 ? 0 : (int) (remainingMillis * 1000 / (timeLimitSeconds * 1000L));
        timeBar.setValue(Math.max(0, Math.min(1000, value)));
    }

    // ---------------------------------------------------------------------
    // 화면 구성
    // ---------------------------------------------------------------------

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        stageLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        stageLabel.setForeground(new Color(200, 190, 255));
        stageLabel.setPreferredSize(new Dimension(200, 30));
        bar.add(stageLabel, BorderLayout.WEST);

        JPanel timerBox = new JPanel(new BorderLayout(0, 4));
        timerBox.setOpaque(false);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        timerLabel.setForeground(TIMER_NORMAL);
        timerLabel.setName("timerLabel");
        timeBar.setPreferredSize(new Dimension(360, 8));
        timeBar.setBorderPainted(false);
        timeBar.setBackground(new Color(60, 50, 100));
        timerBox.add(timerLabel, BorderLayout.CENTER);
        JPanel barHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        barHolder.setOpaque(false);
        barHolder.add(timeBar);
        timerBox.add(barHolder, BorderLayout.SOUTH);
        bar.add(timerBox, BorderLayout.CENTER);

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightBox.setOpaque(false);
        rightBox.setPreferredSize(new Dimension(200, 44));
        pauseButton.setName("pauseButton");
        pauseButton.setPreferredSize(new Dimension(170, 40));
        rightBox.add(pauseButton);
        bar.add(rightBox, BorderLayout.EAST);
        return bar;
    }

    private JPanel wrapSide(CombatantView view) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setPreferredSize(new Dimension(230, 0));
        holder.add(view, BorderLayout.CENTER);
        return holder;
    }

    private JPanel createQuestionCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CARD_BORDER, 3, true), new EmptyBorder(16, 18, 16, 18)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        // 0: 문제
        c.gridy = 0;
        c.insets = new Insets(0, 0, 10, 0);
        questionArea.setName("questionArea");
        questionScroll.setName("questionScroll");
        card.add(questionScroll, c);

        // 1: 코드 (줄바꿈·들여쓰기 유지, 스크롤)
        codeArea.setName("codeArea");
        codeArea.setEditable(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        codeArea.setTabSize(4);
        codeArea.setLineWrap(false);
        codeArea.setBackground(CODE_BG);
        codeArea.setForeground(CODE_FG);
        codeArea.setCaretColor(CODE_FG);
        codeArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        codeScroll.setBorder(new LineBorder(new Color(90, 80, 140), 1));
        codeScroll.setPreferredSize(new Dimension(100, 170));
        codeScroll.setMinimumSize(new Dimension(100, 90));
        codeScroll.getVerticalScrollBar().setUnitIncrement(16);
        c.gridy = 1;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        card.add(codeScroll, c);


        // 2: 답안 입력
        c.gridy = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        answerPanel.setOpaque(false);
        choiceArea = createChoiceArea();
        inputArea = createInputArea();
        card.add(answerPanel, c);

        // 3: 결과 + 해설
        c.gridy = 3;
        c.insets = new Insets(10, 0, 0, 0);
        resultPanel.setName("resultPanel");
        resultPanel.setBackground(Color.WHITE);
        resultTitle.setName("resultTitle");
        resultTitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        resultDetail.setName("resultDetail");
        resultDetail.setRows(0);
        resultScroll.setName("resultScroll");
        resultPanel.add(resultTitle, BorderLayout.NORTH);
        resultPanel.add(resultScroll, BorderLayout.CENTER);
        card.add(resultPanel, c);

        // 4: 전투 종료 문구 + 복귀 버튼 / 다음 문제 버튼
        c.gridy = 4;
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        endLabel.setName("endLabel");
        endLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        endLabel.setOpaque(true);
        endLabel.setBackground(new Color(45, 35, 80));
        endLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        bottom.add(endLabel, BorderLayout.CENTER);
        returnButton.setName("returnButton");
        bottom.add(returnButton, BorderLayout.EAST);
        JPanel nextHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        nextHolder.setOpaque(false);
        nextButton.setName("nextButton");
        nextHolder.add(nextButton);
        bottom.add(nextHolder, BorderLayout.SOUTH);
        card.add(bottom, c);

        // 5: 코드가 없는 문제에서 남는 공간을 아래쪽에서 채운다
        c.gridy = 5;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        filler.setOpaque(false);
        card.add(filler, c);

        return card;
    }

    private JPanel createPopupPanel() {
        JPanel holder = new JPanel(new GridBagLayout());
        holder.setName("popupPanel");
        holder.setOpaque(false);

        popupBox.setName("popupBox");
        popupBox.setBackground(new Color(40, 32, 76));
        popupBox.setPreferredSize(new Dimension(520, 280));
        popupTitle.setName("popupTitle");
        popupTitle.setFont(new Font("SansSerif", Font.BOLD, 30));
        popupMessage.setName("popupMessage");
        popupMessage.setRows(3);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        popupPrimary.setName("popupPrimary");
        popupSecondary.setName("popupSecondary");
        popupPrimary.setPreferredSize(new Dimension(200, 46));
        buttons.add(popupPrimary);
        buttons.add(popupSecondary);

        popupBox.add(popupTitle, BorderLayout.NORTH);
        popupBox.add(popupMessage, BorderLayout.CENTER);
        popupBox.add(buttons, BorderLayout.SOUTH);
        holder.add(popupBox);
        return holder;
    }

    private JPanel createChoiceArea() {
        JPanel area = new JPanel(new BorderLayout(0, 10));
        area.setOpaque(false);
        choicePanel.setOpaque(false);
        area.add(choicePanel, BorderLayout.CENTER);

        JPanel submitRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        submitRow.setOpaque(false);
        choiceSubmitButton.setName("choiceSubmitButton");
        choiceSubmitButton.setPreferredSize(new Dimension(160, 44));
        submitRow.add(choiceSubmitButton);
        area.add(submitRow, BorderLayout.SOUTH);
        return area;
    }

    private JPanel createInputArea() {
        JPanel area = new JPanel(new BorderLayout(10, 0));
        area.setOpaque(false);
        inputField.setName("inputField");
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 18));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CARD_BORDER, 2, true), new EmptyBorder(6, 10, 6, 10)));
        inputField.addActionListener(e -> submitInput()); // Enter로 제출

        JLabel hint = new JLabel("답을 입력하고 Enter 또는 제출");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hint.setForeground(new Color(110, 100, 150));

        inputSubmitButton.setName("inputSubmitButton");
        inputSubmitButton.setPreferredSize(new Dimension(160, 44));
        area.add(hint, BorderLayout.NORTH);
        area.add(inputField, BorderLayout.CENTER);
        area.add(inputSubmitButton, BorderLayout.EAST);
        return area;
    }

    private void buildChoices(List<String> choices) {
        for (JToggleButton b : choiceButtons) {
            choiceGroup.remove(b);
        }
        choiceButtons.clear();
        choicePanel.removeAll();
        choicePanel.setLayout(new GridLayout(0, choices.size() <= 2 ? 1 : 2, 10, 10));

        for (int i = 0; i < choices.size(); i++) {
            JToggleButton b = new JToggleButton(choiceHtml(i + 1, choices.get(i)));
            b.setName("choice" + (i + 1));
            b.setToolTipText(choices.get(i));
            b.setFont(new Font("SansSerif", Font.PLAIN, 15));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false); // setOpaque보다 먼저 불러야 배경색이 칠해진다
            b.setOpaque(true);
            b.setBackground(CHOICE_BG);
            b.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(170, 160, 220), 2, true), new EmptyBorder(8, 10, 8, 10)));
            b.addActionListener(e -> onChoiceSelectionChanged());
            choiceGroup.add(b);
            choiceButtons.add(b);
            choicePanel.add(b);
        }
    }

    private void onChoiceSelectionChanged() {
        for (JToggleButton b : choiceButtons) {
            b.setBackground(b.isSelected() ? CHOICE_SELECTED : CHOICE_BG);
        }
        choiceSubmitButton.setEnabled(phase == Phase.ANSWERING && choiceGroup.getSelection() != null);
    }

    /** 결과 표시 때 정답 보기는 초록, 잘못 고른 보기는 빨강으로 칠한다. */
    private void highlightChoices() {
        if (!(currentQuiz instanceof ChoiceQuiz choiceQuiz)) {
            return;
        }
        for (int i = 0; i < choiceButtons.size(); i++) {
            JToggleButton b = choiceButtons.get(i);
            if (i + 1 == choiceQuiz.getAnswerNo()) {
                b.setBackground(CHOICE_CORRECT);
            } else if (b.isSelected()) {
                b.setBackground(CHOICE_WRONG);
            } else {
                b.setBackground(CHOICE_BG);
            }
        }
    }

    private void resetQuestionView() {
        choiceGroup.clearSelection();
        for (JToggleButton b : choiceButtons) {
            b.setBackground(CHOICE_BG);
        }
        inputField.setText("");
        resultPanel.setVisible(false);
        resultTitle.setText(" ");
        resultDetail.setText("");
        endLabel.setVisible(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        returnButton.setVisible(false);
        returnButton.setEnabled(false);
        choiceSubmitButton.setEnabled(false);
    }

    /** 문제 유형에 맞는 입력 영역 하나만 붙인다 (다른 유형의 높이를 차지하지 않도록). */
    private void showAnswerArea(JPanel area) {
        if (answerPanel.getComponentCount() == 1 && answerPanel.getComponent(0) == area) {
            return;
        }
        answerPanel.removeAll();
        answerPanel.add(area, BorderLayout.CENTER);
    }

    private void refreshLayout() {
        panel.revalidate();
        panel.repaint();
    }

    // ---------------------------------------------------------------------
    // 텍스트 도우미
    // ---------------------------------------------------------------------

    record QuestionParts(String prompt, String code) {
    }

    /**
     * 문제를 설명 문장과 코드로 나눈다.
     *
     * 코드처럼 보이는 첫 줄부터 마지막 줄까지를 코드 블록으로 보고 줄바꿈·들여쓰기를 그대로 둔다.
     * 코드 앞의 설명은 문제 문장이 되고, 코드 뒤에 오는 설명(예: "위 코드의 출력은?")은 문제 문장 끝에 붙인다.
     * 코드처럼 보이는 줄이 없으면 여러 줄이라도 전부 설명 문장으로 표시한다.
     */
    static QuestionParts splitQuestion(String question) {
        String normalized = question.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int firstCode = -1;
        int lastCode = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isCodeLine(lines[i])) {
                if (firstCode < 0) {
                    firstCode = i;
                }
                lastCode = i;
            }
        }
        if (firstCode < 0) {
            return new QuestionParts(joinProse(lines, 0, lines.length), "");
        }
        String before = joinProse(lines, 0, firstCode);
        String after = joinProse(lines, lastCode + 1, lines.length);
        String prompt = before.isEmpty() ? after : (after.isEmpty() ? before : before + "\n" + after);
        String code = String.join("\n", Arrays.copyOfRange(lines, firstCode, lastCode + 1)).stripTrailing();
        return new QuestionParts(prompt, code);
    }

    private static final Pattern CODE_START = Pattern.compile(
            "^(public|private|protected|static|final|abstract|class|interface|enum|import|package|return"
                    + "|new|try|else|case|default|break|continue|throw)\\b.*"
                    + "|^(if|for|while|switch|catch)\\s*\\(.*"
                    + "|^[A-Za-z_][\\w.<>\\[\\], ]*\\s+[A-Za-z_]\\w*\\s*(=[^=].*|;.*|\\(.*)"
                    + "|^[A-Za-z_][\\w.\\[\\]]*\\s*(=[^=]|\\+\\+|--|\\+=|-=).*"
                    + "|^[A-Za-z_][\\w]*(\\.[A-Za-z_]\\w*)+\\s*\\(.*");

    /** 코드 줄인지 추정한다. 한글 설명 문장은 따옴표 밖에 한글이 있으면 코드로 보지 않는다(주석·세미콜론 줄 제외). */
    static boolean isCodeLine(String line) {
        String t = line.strip();
        if (t.isEmpty()) {
            return false;
        }
        if (t.startsWith("//") || t.startsWith("/*") || t.startsWith("*/") || t.startsWith("@")) {
            return true;
        }
        t = stripTrailingComment(t); // 예: acc.withdraw(2000);  // 실패
        if (t.isEmpty()) {
            return true;
        }
        if (t.endsWith(";") || t.endsWith("{") || t.endsWith("}")) {
            return true;
        }
        boolean hangul = containsHangulOutsideQuotes(t);
        if (hangul) {
            return false;
        }
        if (CODE_START.matcher(t).matches()) {
            return true;
        }
        // 들여쓴 줄에 코드 기호가 있으면 코드 (예: 줄이 나뉜 메서드 인자)
        return Character.isWhitespace(line.charAt(0)) && t.matches(".*[(){}\\[\\];=].*");
    }

    /** 따옴표 밖의 // 주석을 떼어 낸다. */
    private static String stripTrailingComment(String s) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
            } else if (ch == '\'' && !inString) {
                inChar = !inChar;
            } else if (!inString && !inChar && ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                return s.substring(0, i).strip();
            }
        }
        return s;
    }

    private static boolean containsHangulOutsideQuotes(String s) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && (inString || inChar)) {
                i++;
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
            } else if (ch == '\'' && !inString) {
                inChar = !inChar;
            } else if (!inString && !inChar && ch >= '\uAC00' && ch <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }

    /** 설명 줄들을 합친다. 앞뒤 빈 줄은 지우고 각 줄의 앞뒤 공백만 정리한다. */
    private static String joinProse(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            String t = lines[i].strip();
            if (sb.length() > 0 || !t.isEmpty()) {
                sb.append(t).append('\n');
            }
        }
        return sb.toString().strip();
    }

    private static String correctAnswerText(Quiz quiz) {
        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            int no = choiceQuiz.getAnswerNo();
            return no + ". " + choiceQuiz.getChoices().get(no - 1);
        }
        if (quiz instanceof InputQuiz inputQuiz) {
            return inputQuiz.getAnswer();
        }
        return "-";
    }

    private static String choiceHtml(int no, String text) {
        return "<html><div style='width:230px; color:#231e3c'><b>" + no + "</b>&nbsp;&nbsp;" + escapeHtml(text)
                + "</div></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 줄바꿈 텍스트를 감싸는 세로 스크롤. 내용이 maxLines 줄 이하면 그 높이만 차지하고,
     * 넘치면 maxLines 줄 높이로 고정한 채 스크롤한다. 배경은 투명하게 두어 기존 디자인을 유지한다.
     */
    private static JScrollPane createCappedScroll(JTextArea area, int maxLines) {
        JScrollPane scroll = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
                Insets in = getInsets();
                int cap = lineHeight * maxLines + in.top + in.bottom;
                return new Dimension(d.width, Math.min(d.height, cap));
            }
        };
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JTextArea createWrappingText(Font font, Color color) {
        JTextArea area = new JTextArea();
        area.setFont(font);
        area.setForeground(color);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false); // 한글 문장도 칸에 맞춰 줄바꿈
        area.setBorder(null);
        return area;
    }

    // ---------------------------------------------------------------------
    // 캐릭터 + HP 표시. 이미지가 없으므로 도형으로 그린다.
    // ---------------------------------------------------------------------

    private static final class CombatantView extends JComponent {

        private static final long serialVersionUID = 1L;

        private final boolean player;
        private String displayName;
        private int hp;
        private int maxHp;

        CombatantView(String name, boolean player) {
            this.displayName = name;
            this.player = player;
            setPreferredSize(new Dimension(230, 400));
        }

        void setDisplayName(String name) {
            this.displayName = name;
            repaint();
        }

        void setHp(int hp, int maxHp) {
            this.maxHp = Math.max(0, maxHp);
            this.hp = Math.max(0, Math.min(hp, this.maxHp));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // 이름표 + HP
            int boxX = 6;
            int boxY = 10;
            int boxW = w - 12;
            int boxH = 96;
            g2.setColor(new Color(20, 16, 40, 230));
            g2.fillRoundRect(boxX, boxY, boxW, boxH, 14, 14);
            g2.setColor(new Color(220, 210, 255));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(boxX, boxY, boxW, boxH, 14, 14);

            g2.setFont(new Font("SansSerif", Font.BOLD, 17));
            g2.setColor(Color.WHITE);
            g2.drawString(displayName == null ? "" : displayName, boxX + 12, boxY + 26);

            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            String hpText = "HP " + hp + " / " + maxHp;
            g2.drawString(hpText, boxX + 12, boxY + 50);

            // HP 막대
            int barX = boxX + 12;
            int barY = boxY + 58;
            int barW = boxW - 24;
            g2.setColor(new Color(70, 60, 100));
            g2.fillRoundRect(barX, barY, barW, 8, 8, 8);
            if (maxHp > 0) {
                g2.setColor(player ? new Color(90, 200, 255) : new Color(240, 90, 110));
                g2.fillRoundRect(barX, barY, barW * hp / maxHp, 8, 8, 8);
            }

            // 하트 (최대 HP가 크면 숫자와 막대만)
            int heart = 18;
            int gap = 6;
            int hearts = maxHp <= MAX_HEART_ICONS ? maxHp : 0;
            for (int i = 0; i < hearts; i++) {
                int hx = barX + i * (heart + gap);
                paintHeart(g2, hx, barY + 14, heart, i < hp);
            }

            // 몸체
            int bodyTop = boxY + boxH + 30;
            int bodyH = Math.min(h - bodyTop - 20, 240);
            if (bodyH > 60) {
                if (player) {
                    paintPlayer(g2, w / 2, bodyTop, bodyH);
                } else {
                    paintMonster(g2, w / 2, bodyTop, bodyH);
                }
            }
            g2.dispose();
        }

        private void paintHeart(Graphics2D g2, int x, int y, int size, boolean filled) {
            Path2D.Double p = new Path2D.Double();
            double s = size;
            p.moveTo(x + s / 2, y + s * 0.95);
            p.curveTo(x - s * 0.1, y + s * 0.5, x + s * 0.05, y - s * 0.05, x + s / 2, y + s * 0.28);
            p.curveTo(x + s * 0.95, y - s * 0.05, x + s * 1.1, y + s * 0.5, x + s / 2, y + s * 0.95);
            p.closePath();
            if (filled) {
                g2.setColor(new Color(245, 110, 120));
                g2.fill(p);
            } else {
                g2.setColor(new Color(110, 100, 140));
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(p);
            }
        }

        private void paintPlayer(Graphics2D g2, int cx, int top, int height) {
            int headR = height / 4;
            int bodyW = (int) (headR * 2.2);
            int bodyH = height - headR * 2;
            // 몸
            g2.setColor(new Color(235, 240, 255));
            g2.fillRoundRect(cx - bodyW / 2, top + headR * 2 - 6, bodyW, bodyH, 30, 30);
            // 머리
            g2.setColor(new Color(80, 160, 240));
            g2.fillOval(cx - headR, top, headR * 2, headR * 2);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, headR));
            centerText(g2, "0", cx, top + headR);
            // 눈
            g2.setColor(new Color(30, 40, 80));
            g2.fillOval(cx + headR / 3, top + headR - 4, 8, 10);
            g2.setColor(new Color(160, 200, 255));
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            centerText(g2, "PLAYER", cx, top + headR * 2 + bodyH / 2);
        }

        private void paintMonster(Graphics2D g2, int cx, int top, int height) {
            int bw = (int) (height * 0.85);
            int bh = (int) (height * 0.8);
            int y = top + (height - bh) / 2;
            g2.setColor(new Color(110, 70, 190));
            g2.fillRoundRect(cx - bw / 2, y, bw, bh, bw / 2, bw / 2);
            g2.setColor(new Color(170, 130, 255));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(cx - bw / 2, y, bw, bh, bw / 2, bw / 2);

            // 화난 눈
            int eyeY = y + bh / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(cx - bw / 4 - 10, eyeY, 20, 16);
            g2.fillOval(cx + bw / 4 - 10, eyeY, 20, 16);
            g2.setColor(new Color(30, 20, 50));
            g2.fillOval(cx - bw / 4 - 4, eyeY + 4, 9, 9);
            g2.fillOval(cx + bw / 4 - 4, eyeY + 4, 9, 9);
            g2.setStroke(new BasicStroke(4f));
            g2.drawLine(cx - bw / 4 - 14, eyeY - 10, cx - bw / 4 + 10, eyeY - 2);
            g2.drawLine(cx + bw / 4 + 14, eyeY - 10, cx + bw / 4 - 10, eyeY - 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
            centerText(g2, "</>", cx, y + bh / 4);
        }

        private static void centerText(Graphics2D g2, String text, int cx, int cy) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}
