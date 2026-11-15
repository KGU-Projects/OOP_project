package game.ui.screens;

import game.common.GameConstants;
import game.model.GameState;
import game.model.ItemType;
import game.model.SaveData;
import game.model.Stage;
import game.model.StageType;
import game.model.Tile;
import game.model.object.BossObject;
import game.model.object.ButtonObject;
import game.model.object.DoorObject;
import game.model.object.GameObject;
import game.model.object.ItemObject;
import game.model.object.KeyObject;
import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.BaseScreen;
import game.ui.ScreenManager;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * BaseScreen을 상속하는 실제 플랫포머 플레이 화면.
 * ScreenManager의 onEnter() / onExit() 생명주기에 맞춰 게임 루프와 키 포커스를 제어한다.
 */
public class GameScreen extends BaseScreen {

    /** 나중에 QuizScreen / UserService를 연결하기 위한 콜백 인터페이스 */
    public interface GameListener {
        default void onQuizRequested(Quiz quiz, Consumer<Boolean> onResult) {
        }

        default void onStageCleared(SaveData saveData) {
        }

        default void onGameOver(int finalScore) {
        }

        default void onExitToMenu() {
        }
    }

    // 렌더링 및 물리 상수 (타일 단위)
    private static final int TILE_SIZE = 44;
    private static final int HUD_HEIGHT = 56;
    private static final int FOOTER_HEIGHT = 34;

    private static final double MOVE_SPEED = 6.2; // tiles / sec
    private static final double GRAVITY = 26.0; // tiles / sec^2
    private static final double JUMP_VELOCITY = -11.8; // tiles / sec
    private static final double MAX_FALL_SPEED = 16.0; // tiles / sec
    private static final double EPS = 1e-4;
    private static final int DEFAULT_MAX_HP = 5;

    private final GameCanvas canvas;
    private final Stage initialStage;
    private final SaveData initialSaveData;
    private final IntFunction<Quiz> quizProvider;
    private final GameListener listener;
    private final boolean useExternalQuizScreen;

    private GameState state;
    private double playerX;
    private double playerY;
    private double velX;
    private double velY;
    private boolean onGround;
    private boolean facingRight = true;

    // 입력 플래그
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean jumpPressed;

    // 상태 메시지 & 애니메이션
    private String bannerMessage = "";
    private Color bannerColor = new Color(245, 197, 66);
    private double bannerTimer = 0.0;
    private double animTime = 0.0;
    private boolean paused = false;

    private final Timer gameLoop;

    /** 단독 테스트 및 기본 등록용 생성자 */
    public GameScreen(ScreenManager screenManager) {
        this(screenManager, createDemoStage(), createDemoQuizzes()::get, new GameListener() {
        }, false, null);
    }

    /** ESC 메뉴 복귀 동작만 간단히 지정할 때 사용하는 생성자 */
    public GameScreen(ScreenManager screenManager, Runnable onExitToMenu) {
        this(screenManager, createDemoStage(), createDemoQuizzes()::get, new GameListener() {
            @Override
            public void onExitToMenu() {
                if (onExitToMenu != null) {
                    onExitToMenu.run();
                }
            }
        }, false, null);
    }

    public GameScreen(ScreenManager screenManager,
            Stage stage,
            IntFunction<Quiz> quizProvider,
            GameListener listener,
            boolean useExternalQuizScreen,
            SaveData saveData) {
        super(screenManager, new BorderLayout());
        // BaseScreen의 기본 여백(40,50,40,50)을 제거해 게임 캔버스가 화면 전체를 채우도록 설정
        this.panel.setBorder(BorderFactory.createEmptyBorder());

        this.initialStage = stage;
        this.initialSaveData = saveData;
        this.quizProvider = quizProvider;
        this.listener = listener != null ? listener : new GameListener() {
        };
        this.useExternalQuizScreen = useExternalQuizScreen;

        this.canvas = new GameCanvas();
        this.panel.add(canvas, BorderLayout.CENTER);

        setupKeyBindings();
        loadStage(stage, saveData);

        this.gameLoop = new Timer(16, e -> {
            update(0.016);
            canvas.repaint();
        });
    }

    // -------------------------------------------------------------------------
    // Screen 생명주기 (ScreenManager.show 호출 시 자동 실행)
    // -------------------------------------------------------------------------

    @Override
    public void onEnter() {
        // 처음 진입하거나 이전 판이 클리어/게임오버로 끝난 상태면 스테이지를 새로 시작하고,
        // 퀴즈 화면 등에서 되돌아온 경우에는 진행 중이던 상태를 그대로 이어간다.
        if (state == null || state.isStageCleared() || state.isDead()) {
            loadStage(initialStage, initialSaveData);
        }
        paused = false;
        resetInputFlags();
        if (!gameLoop.isRunning()) {
            gameLoop.start();
        }
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    @Override
    public void onExit() {
        resetInputFlags();
        if (gameLoop.isRunning()) {
            gameLoop.stop();
        }
    }

    /** 스테이지 시작 또는 다음 스테이지 로드 */
    public final void loadStage(Stage stage, SaveData saveData) {
        this.state = new GameState(stage, DEFAULT_MAX_HP, saveData);
        this.playerX = stage.getStartX();
        this.playerY = stage.getStartY();
        this.velX = 0;
        this.velY = 0;
        this.onGround = false;
        this.facingRight = true;
        resetInputFlags();
        this.paused = false;

        showBanner("STAGE " + stage.getNumber() + " - " + stage.getTitle(), new Color(130, 210, 255), 2.5);
    }

    private void resetInputFlags() {
        leftPressed = false;
        rightPressed = false;
        jumpPressed = false;
    }

    // -------------------------------------------------------------------------
    // 입력 바인딩
    // -------------------------------------------------------------------------

    private void setupKeyBindings() {
        InputMap im = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = canvas.getActionMap();

        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "left_down", () -> leftPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "left_up", () -> leftPressed = false);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "a_down", () -> leftPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "a_up", () -> leftPressed = false);

        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "right_down", () -> rightPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "right_up", () -> rightPressed = false);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "d_down", () -> rightPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "d_up", () -> rightPressed = false);

        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "jump_space_down",
                () -> jumpPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "jump_space_up", () -> jumpPressed = false);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "jump_w_down", () -> jumpPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "jump_w_up", () -> jumpPressed = false);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "jump_up_down", () -> jumpPressed = true);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "jump_up_up", () -> jumpPressed = false);

        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "interact", this::tryInteract);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, false), "use_potion", this::tryUsePotion);
        bindKey(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "exit_menu", listener::onExitToMenu);
    }

    private void bindKey(InputMap im, ActionMap am, KeyStroke stroke, String name, Runnable action) {
        im.put(stroke, name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // -------------------------------------------------------------------------
    // 물리 & 게임 로직 업데이트
    // -------------------------------------------------------------------------

    private void update(double dt) {
        animTime += dt;
        if (bannerTimer > 0) {
            bannerTimer = Math.max(0, bannerTimer - dt);
        }

        if (paused || state.isStageCleared() || state.isDead()) {
            return;
        }

        velX = 0;
        if (leftPressed && !rightPressed) {
            velX = -MOVE_SPEED;
            facingRight = false;
        } else if (rightPressed && !leftPressed) {
            velX = MOVE_SPEED;
            facingRight = true;
        }

        if (jumpPressed && onGround) {
            velY = JUMP_VELOCITY;
            onGround = false;
        }
        velY = Math.min(MAX_FALL_SPEED, velY + GRAVITY * dt);

        moveHorizontal(velX * dt);
        moveVertical(velY * dt);
        checkAutoCollectObjects();
    }

    private void moveHorizontal(double dx) {
        if (dx == 0) {
            return;
        }
        double nextX = playerX + dx;
        double w = GameConstants.PLAYER_WIDTH;
        double h = GameConstants.PLAYER_HEIGHT;

        if (dx > 0) {
            int rightTile = (int) Math.floor(nextX + w - EPS);
            int topTile = (int) Math.floor(playerY + EPS);
            int bottomTile = (int) Math.floor(playerY + h - EPS);
            for (int ty = topTile; ty <= bottomTile; ty++) {
                if (isBlockedTile(rightTile, ty)) {
                    nextX = rightTile - w;
                    break;
                }
            }
        } else {
            int leftTile = (int) Math.floor(nextX + EPS);
            int topTile = (int) Math.floor(playerY + EPS);
            int bottomTile = (int) Math.floor(playerY + h - EPS);
            for (int ty = topTile; ty <= bottomTile; ty++) {
                if (isBlockedTile(leftTile, ty)) {
                    nextX = leftTile + 1.0;
                    break;
                }
            }
        }
        playerX = nextX;
    }

    private void moveVertical(double dy) {
        double nextY = playerY + dy;
        double w = GameConstants.PLAYER_WIDTH;
        double h = GameConstants.PLAYER_HEIGHT;
        onGround = false;

        if (dy > 0) {
            int bottomTile = (int) Math.floor(nextY + h - EPS);
            int leftTile = (int) Math.floor(playerX + EPS);
            int rightTile = (int) Math.floor(playerX + w - EPS);
            for (int tx = leftTile; tx <= rightTile; tx++) {
                if (isBlockedTile(tx, bottomTile)) {
                    nextY = bottomTile - h;
                    velY = 0;
                    onGround = true;
                    break;
                }
            }
        } else if (dy < 0) {
            int topTile = (int) Math.floor(nextY + EPS);
            int leftTile = (int) Math.floor(playerX + EPS);
            int rightTile = (int) Math.floor(playerX + w - EPS);
            for (int tx = leftTile; tx <= rightTile; tx++) {
                if (isBlockedTile(tx, topTile)) {
                    nextY = topTile + 1.0;
                    velY = 0;
                    break;
                }
            }
        }
        playerY = nextY;
    }

    private boolean isBlockedTile(int tx, int ty) {
        Stage stage = state.getStage();
        if (stage.isSolidTile(tx, ty)) {
            return true;
        }
        for (GameObject obj : stage.getObjects()) {
            if (obj instanceof DoorObject door && !state.isDoorOpen(door.getId())) {
                if (tx >= door.getX() && tx < door.getX() + door.getWidth()
                        && ty >= door.getY() && ty < door.getY() + door.getHeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkAutoCollectObjects() {
        for (GameObject obj : state.getStage().getObjects()) {
            if (state.isObjectCollected(obj.getId())) {
                continue;
            }
            if (!overlapsPlayer(obj, 0.0)) {
                continue;
            }

            if (obj instanceof ItemObject item) {
                state.markObjectCollected(item.getId());
                state.addItem(item.getItemType(), item.getCount());
                state.addScore(50 * item.getCount());
                showBanner(item.getItemType().name() + " x" + item.getCount() + " 획득!", new Color(120, 235, 160), 2.0);
            } else if (obj instanceof KeyObject) {
                state.markObjectCollected(obj.getId());
                state.addItem(ItemType.KEY, 1);
                state.addScore(200);
                triggerStageClear();
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // 상호작용 (버튼 퀴즈, 보스 퀴즈, 체력 물약)
    // -------------------------------------------------------------------------

    private void tryInteract() {
        if (paused || state.isStageCleared() || state.isDead()) {
            return;
        }

        GameObject target = findNearbyInteractable();
        if (target == null) {
            return;
        }

        if (target instanceof ButtonObject button && !state.isButtonSolved(button.getId())) {
            Quiz quiz = quizProvider.apply(button.getQuizId());
            if (quiz == null) {
                solveButton(button);
                return;
            }
            openQuiz(quiz, correct -> {
                if (correct) {
                    solveButton(button);
                } else {
                    state.damage(1);
                    if (state.isDead()) {
                        showBanner("체력이 모두 소진되었습니다!", new Color(255, 105, 105), 3.0);
                        listener.onGameOver(state.getScore());
                    } else {
                        showBanner("오답! 체력이 1 감소했습니다.", new Color(255, 120, 120), 2.0);
                    }
                }
            });
        } else if (target instanceof BossObject boss && state.getBossHp() > 0) {
            int bossQuizId = state.getStage().getNumber() * 100 + (state.getBossMaxHp() - state.getBossHp() + 1);
            Quiz quiz = quizProvider.apply(bossQuizId);
            if (quiz == null) {
                quiz = quizProvider.apply(1);
            }
            if (quiz == null) {
                damageBossAndCheckClear(boss);
                return;
            }
            openQuiz(quiz, correct -> {
                if (correct) {
                    damageBossAndCheckClear(boss);
                } else {
                    state.damage(1);
                    if (state.isDead()) {
                        listener.onGameOver(state.getScore());
                    } else {
                        showBanner(boss.getName() + "의 반격! 체력 -1", new Color(255, 120, 120), 2.0);
                    }
                }
            });
        }
    }

    private void solveButton(ButtonObject button) {
        state.markButtonSolved(button.getId());
        for (String doorId : button.getDoorIds()) {
            state.openDoor(doorId);
        }
        state.addScore(100);
        showBanner("정답! 문이 열렸습니다 (+100점)", new Color(110, 240, 170), 2.2);
    }

    private void damageBossAndCheckClear(BossObject boss) {
        state.damageBoss(1);
        state.addScore(150);
        if (state.getBossHp() <= 0) {
            showBanner(boss.getName() + " 격파!", new Color(255, 215, 90), 2.5);
            if (state.getStage().getType() == StageType.BOSS) {
                triggerStageClear();
            }
        } else {
            showBanner("명중! " + boss.getName() + " 남은 체력: " + state.getBossHp(), new Color(255, 210, 110), 2.0);
        }
    }

    private void tryUsePotion() {
        if (paused || state.isStageCleared() || state.isDead()) {
            return;
        }
        if (state.getHp() >= state.getMaxHp()) {
            showBanner("이미 최대 체력입니다.", new Color(200, 210, 230), 1.5);
            return;
        }
        if (state.consumeItem(ItemType.HEALTH_POTION)) {
            state.heal(1);
            showBanner("체력 물약 사용! (HP +1)", new Color(120, 240, 170), 1.8);
        } else {
            showBanner("사용할 체력 물약이 없습니다.", new Color(220, 150, 150), 1.5);
        }
    }

    private void triggerStageClear() {
        state.setStageCleared(true);
        SaveData saveData = state.toSaveData(LocalDateTime.now());
        showBanner("STAGE " + state.getStage().getNumber() + " CLEAR!", new Color(255, 220, 95), 4.0);
        listener.onStageCleared(saveData);
    }

    private GameObject findNearbyInteractable() {
        for (GameObject obj : state.getStage().getObjects()) {
            if (obj instanceof ButtonObject button && !state.isButtonSolved(button.getId())) {
                if (overlapsPlayer(button, 0.25)) {
                    return button;
                }
            } else if (obj instanceof BossObject boss && state.getBossHp() > 0) {
                if (overlapsPlayer(boss, 0.45)) {
                    return boss;
                }
            }
        }
        return null;
    }

    private boolean overlapsPlayer(GameObject obj, double margin) {
        double px1 = playerX - margin;
        double py1 = playerY - margin;
        double px2 = playerX + GameConstants.PLAYER_WIDTH + margin;
        double py2 = playerY + GameConstants.PLAYER_HEIGHT + margin;

        double ox1 = obj.getX();
        double oy1 = obj.getY();
        double ox2 = obj.getX() + obj.getWidth();
        double oy2 = obj.getY() + obj.getHeight();

        return px1 < ox2 && px2 > ox1 && py1 < oy2 && py2 > oy1;
    }

    private void openQuiz(Quiz quiz, Consumer<Boolean> onResult) {
        paused = true;
        resetInputFlags();

        Consumer<Boolean> resumeAndHandle = correct -> {
            paused = false;
            canvas.requestFocusInWindow();
            onResult.accept(correct);
        };

        if (useExternalQuizScreen) {
            listener.onQuizRequested(quiz, resumeAndHandle);
        } else {
            SwingUtilities.invokeLater(() -> showBuiltInQuizDialog(quiz, resumeAndHandle));
        }
    }

    private void showBuiltInQuizDialog(Quiz quiz, Consumer<Boolean> onResult) {
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        JDialog dialog = new JDialog(owner, "QUIZ #" + quiz.getId(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(28, 33, 46));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JLabel title = new JLabel("<html><body style='width:380px'>" + quiz.getQuestion() + "</body></html>");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        root.add(title, BorderLayout.NORTH);

        final boolean[] answeredCorrect = { false };

        if (quiz instanceof ChoiceQuiz choiceQuiz) {
            JPanel choicesPanel = new JPanel(new GridLayout(0, 1, 8, 8));
            choicesPanel.setOpaque(false);
            List<String> choices = choiceQuiz.getChoices();
            for (int i = 0; i < choices.size(); i++) {
                int optionNo = i + 1;
                JButton btn = new JButton(optionNo + ". " + choices.get(i));
                btn.setFocusPainted(false);
                btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
                btn.setBackground(new Color(48, 58, 80));
                btn.setForeground(Color.WHITE);
                btn.addActionListener(e -> {
                    answeredCorrect[0] = quiz.isCorrect(String.valueOf(optionNo));
                    showExplanationIfPresent(dialog, quiz, answeredCorrect[0]);
                    dialog.dispose();
                });
                choicesPanel.add(btn);
            }
            root.add(choicesPanel, BorderLayout.CENTER);
        } else if (quiz instanceof InputQuiz) {
            JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
            inputPanel.setOpaque(false);
            JTextField field = new JTextField();
            field.setFont(new Font("SansSerif", Font.PLAIN, 15));
            JButton submitBtn = new JButton("제출");
            submitBtn.setBackground(new Color(70, 130, 220));
            submitBtn.setForeground(Color.WHITE);

            Runnable submitAction = () -> {
                answeredCorrect[0] = quiz.isCorrect(field.getText());
                showExplanationIfPresent(dialog, quiz, answeredCorrect[0]);
                dialog.dispose();
            };
            field.addActionListener(e -> submitAction.run());
            submitBtn.addActionListener(e -> submitAction.run());

            inputPanel.add(field, BorderLayout.CENTER);
            inputPanel.add(submitBtn, BorderLayout.EAST);
            root.add(inputPanel, BorderLayout.CENTER);
        }

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(canvas);
        dialog.setVisible(true);

        onResult.accept(answeredCorrect[0]);
    }

    private void showExplanationIfPresent(JDialog parent, Quiz quiz, boolean correct) {
        String header = correct ? "정답입니다!" : "오답입니다!";
        String body = quiz.getExplanation().isBlank() ? header : header + "\n\n해설: " + quiz.getExplanation();
        JOptionPane.showMessageDialog(parent, body, correct ? "CLEAR" : "MISS",
                correct ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void showBanner(String message, Color color, double durationSec) {
        this.bannerMessage = message;
        this.bannerColor = color;
        this.bannerTimer = durationSec;
    }

    // -------------------------------------------------------------------------
    // 렌더링 전용 내부 캔버스 (JPanel)
    // -------------------------------------------------------------------------

    private class GameCanvas extends JPanel {

        GameCanvas() {
            setFocusable(true);
            setBackground(new Color(20, 24, 34));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(960, 640);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int viewW = getWidth();
            int viewH = getHeight();
            int worldAreaH = Math.max(200, viewH - HUD_HEIGHT - FOOTER_HEIGHT);

            Stage stage = state.getStage();
            int mapPixelW = stage.getWidth() * TILE_SIZE;
            int mapPixelH = stage.getHeight() * TILE_SIZE;

            double targetCamX = (playerX + GameConstants.PLAYER_WIDTH / 2.0) * TILE_SIZE - viewW / 2.0;
            double targetCamY = (playerY + GameConstants.PLAYER_HEIGHT / 2.0) * TILE_SIZE - worldAreaH / 2.0;

            int camX = mapPixelW <= viewW
                    ? -(viewW - mapPixelW) / 2
                    : (int) Math.max(0, Math.min(mapPixelW - viewW, targetCamX));
            int camY = mapPixelH <= worldAreaH
                    ? -(worldAreaH - mapPixelH) / 2
                    : (int) Math.max(0, Math.min(mapPixelH - worldAreaH, targetCamY));

            g2.setClip(0, HUD_HEIGHT, viewW, worldAreaH);
            drawThemeBackground(g2, viewW, worldAreaH, stage.getTheme());

            g2.translate(-camX, HUD_HEIGHT - camY);
            drawTerrain(g2, stage);
            drawObjects(g2, stage);
            drawPlayer(g2);
            g2.translate(camX, -(HUD_HEIGHT - camY));
            g2.setClip(null);

            drawTopHud(g2, viewW);
            drawFooterBar(g2, viewW, viewH);

            if (bannerTimer > 0 && !bannerMessage.isBlank()) {
                drawBanner(g2, viewW);
            }
            if (state.isStageCleared()) {
                drawEndOverlay(g2, viewW, viewH,
                        "STAGE " + stage.getNumber() + " CLEAR!",
                        "점수: " + state.getScore() + "  |  보유 열쇠: " + state.getItemCount(ItemType.KEY)
                                + "개  |  [ESC] 메인 메뉴",
                        new Color(255, 215, 85));
            } else if (state.isDead()) {
                drawEndOverlay(g2, viewW, viewH,
                        "GAME OVER",
                        "최종 점수: " + state.getScore() + "  |  [ESC] 메인 메뉴로 돌아가기",
                        new Color(255, 100, 100));
            }

            g2.dispose();
        }
    }

    private void drawThemeBackground(Graphics2D g2, int w, int h, int theme) {
        Color top;
        Color bottom;
        switch (theme) {
            case 2 -> {
                top = new Color(22, 42, 44);
                bottom = new Color(14, 24, 28);
            }
            case 3 -> {
                top = new Color(48, 22, 32);
                bottom = new Color(22, 12, 18);
            }
            default -> {
                top = new Color(25, 32, 48);
                bottom = new Color(15, 19, 30);
            }
        }
        g2.setPaint(new GradientPaint(0, HUD_HEIGHT, top, 0, HUD_HEIGHT + h, bottom));
        g2.fillRect(0, HUD_HEIGHT, w, h);
    }

    private void drawTerrain(Graphics2D g2, Stage stage) {
        Color blockFill = stage.getTheme() == 3 ? new Color(92, 56, 68) : new Color(68, 78, 102);
        Color blockTop = stage.getTheme() == 3 ? new Color(140, 86, 102) : new Color(102, 118, 150);
        Color blockBorder = new Color(36, 42, 58);

        for (int y = 0; y < stage.getHeight(); y++) {
            for (int x = 0; x < stage.getWidth(); x++) {
                if (stage.getTile(x, y) == Tile.SOLID) {
                    int px = x * TILE_SIZE;
                    int py = y * TILE_SIZE;
                    g2.setColor(blockFill);
                    g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);
                    g2.setColor(blockTop);
                    g2.fillRect(px, py, TILE_SIZE, 5);
                    g2.setColor(blockBorder);
                    g2.drawRect(px, py, TILE_SIZE, TILE_SIZE);
                }
            }
        }
    }

    private void drawObjects(Graphics2D g2, Stage stage) {
        GameObject nearby = findNearbyInteractable();

        for (GameObject obj : stage.getObjects()) {
            if (state.isObjectCollected(obj.getId())) {
                continue;
            }
            int px = obj.getX() * TILE_SIZE;
            int py = obj.getY() * TILE_SIZE;
            int pw = obj.getWidth() * TILE_SIZE;
            int ph = obj.getHeight() * TILE_SIZE;

            if (obj instanceof DoorObject door) {
                boolean open = state.isDoorOpen(door.getId());
                if (open) {
                    g2.setColor(new Color(90, 210, 140, 55));
                    g2.drawRect(px + 4, py + 2, pw - 8, ph - 4);
                } else {
                    g2.setColor(new Color(175, 72, 72));
                    g2.fillRoundRect(px + 6, py + 2, pw - 12, ph - 4, 8, 8);
                    g2.setColor(new Color(245, 195, 95));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(px + 6, py + 2, pw - 12, ph - 4, 8, 8);
                    g2.fillOval(px + pw / 2 - 5, py + ph / 2 - 5, 10, 10);
                    g2.setStroke(new BasicStroke(1f));
                }
            } else if (obj instanceof ButtonObject button) {
                boolean solved = state.isButtonSolved(button.getId());
                g2.setColor(new Color(55, 62, 78));
                g2.fillRoundRect(px + 8, py + TILE_SIZE - 12, TILE_SIZE - 16, 12, 6, 6);
                g2.setColor(solved ? new Color(85, 215, 140) : new Color(245, 185, 60));
                g2.fillRoundRect(px + 11, py + 12, TILE_SIZE - 22, TILE_SIZE - 22, 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                drawCenteredText(g2, solved ? "✓" : "?", px + pw / 2, py + ph / 2 + 4);

                if (obj == nearby) {
                    drawPromptTag(g2, "[E] 퀴즈 풀기", px + pw / 2, py - 8);
                }
            } else if (obj instanceof ItemObject item) {
                double bob = Math.sin(animTime * 4.0) * 3.0;
                int iy = (int) (py + bob);
                if (item.getItemType() == ItemType.HEALTH_POTION) {
                    g2.setColor(new Color(235, 75, 95));
                    g2.fillOval(px + 11, iy + 12, TILE_SIZE - 22, TILE_SIZE - 18);
                    g2.setColor(new Color(220, 225, 235));
                    g2.fillRect(px + TILE_SIZE / 2 - 4, iy + 7, 8, 6);
                } else {
                    g2.setColor(new Color(250, 210, 75));
                    g2.fillOval(px + 12, iy + 12, 18, 18);
                }
                if (item.getCount() > 1) {
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    g2.drawString("x" + item.getCount(), px + TILE_SIZE - 16, iy + TILE_SIZE - 6);
                }
            } else if (obj instanceof KeyObject) {
                double bob = Math.sin(animTime * 4.5) * 4.0;
                int ky = (int) (py + bob);
                g2.setColor(new Color(255, 215, 65));
                g2.fillOval(px + 10, ky + 10, 16, 16);
                g2.fillRect(px + 22, ky + 16, 14, 5);
                g2.fillRect(px + 29, ky + 21, 4, 5);
                g2.fillRect(px + 34, ky + 21, 4, 5);
            } else if (obj instanceof BossObject boss && state.getBossHp() > 0) {
                g2.setColor(new Color(185, 55, 95));
                g2.fillRoundRect(px + 4, py + 4, pw - 8, ph - 8, 12, 12);
                g2.setColor(new Color(255, 210, 90));
                g2.drawRoundRect(px + 4, py + 4, pw - 8, ph - 8, 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                drawCenteredText(g2, boss.getName(), px + pw / 2, py - 24);
                if (obj == nearby) {
                    drawPromptTag(g2, "[E] 보스 퀴즈 도전", px + pw / 2, py - 8);
                }
            }
        }
    }

    private void drawPlayer(Graphics2D g2) {
        int px = (int) Math.round(playerX * TILE_SIZE);
        int py = (int) Math.round(playerY * TILE_SIZE);
        int pw = (int) Math.round(GameConstants.PLAYER_WIDTH * TILE_SIZE);
        int ph = (int) Math.round(GameConstants.PLAYER_HEIGHT * TILE_SIZE);

        g2.setColor(new Color(85, 175, 255));
        g2.fillRoundRect(px, py, pw, ph, 10, 10);
        g2.setColor(new Color(210, 238, 255));
        g2.drawRoundRect(px, py, pw, ph, 10, 10);

        int eyeX = facingRight ? px + pw - 11 : px + 5;
        g2.setColor(new Color(20, 28, 44));
        g2.fillRoundRect(eyeX, py + 8, 6, 7, 3, 3);
    }

    private void drawTopHud(Graphics2D g2, int viewW) {
        g2.setColor(new Color(15, 18, 26, 235));
        g2.fillRect(0, 0, viewW, HUD_HEIGHT);
        g2.setColor(new Color(48, 56, 76));
        g2.drawLine(0, HUD_HEIGHT - 1, viewW, HUD_HEIGHT - 1);

        Stage stage = state.getStage();
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("STAGE " + stage.getNumber() + " · " + stage.getTitle(), 20, 24);

        g2.setColor(new Color(165, 178, 204));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString(stage.getConcept(), 20, 43);

        StringBuilder hearts = new StringBuilder("HP: ");
        for (int i = 0; i < state.getMaxHp(); i++) {
            hearts.append(i < state.getHp() ? "♥ " : "♡ ");
        }
        g2.setColor(new Color(255, 105, 120));
        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.drawString(hearts.toString(), viewW / 2 - 130, 34);

        g2.setColor(new Color(245, 220, 120));
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        String rightStatus = String.format("SCORE: %d   |   POTION: %d   |   KEY: %d",
                state.getScore(),
                state.getItemCount(ItemType.HEALTH_POTION),
                state.getItemCount(ItemType.KEY));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(rightStatus, viewW - fm.stringWidth(rightStatus) - 22, 34);
    }

    private void drawFooterBar(Graphics2D g2, int viewW, int viewH) {
        int y = viewH - FOOTER_HEIGHT;
        g2.setColor(new Color(15, 18, 26, 235));
        g2.fillRect(0, y, viewW, FOOTER_HEIGHT);
        g2.setColor(new Color(48, 56, 76));
        g2.drawLine(0, y, viewW, y);

        g2.setColor(new Color(180, 192, 215));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String controls = "[A/D / ←→] 이동    [W/↑/Space] 점프    [E] 버튼/보스 상호작용    [H] 체력 물약 사용    [ESC] 메인 메뉴";
        drawCenteredText(g2, controls, viewW / 2, y + 21);
    }

    private void drawPromptTag(Graphics2D g2, String text, int centerX, int bottomY) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int padX = 8;
        int w = fm.stringWidth(text) + padX * 2;
        int h = 20;
        int x = centerX - w / 2;
        int y = bottomY - h;

        g2.setColor(new Color(18, 22, 32, 220));
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(new Color(255, 210, 85));
        g2.drawRoundRect(x, y, w, h, 8, 8);
        g2.drawString(text, x + padX, y + 14);
    }

    private void drawBanner(Graphics2D g2, int viewW) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(bannerMessage) + 32;
        int h = 30;
        int x = (viewW - w) / 2;
        int y = HUD_HEIGHT + 14;

        g2.setColor(new Color(16, 20, 30, 215));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(bannerColor);
        g2.drawRoundRect(x, y, w, h, 12, 12);
        drawCenteredText(g2, bannerMessage, viewW / 2, y + 20);
    }

    private void drawEndOverlay(Graphics2D g2, int viewW, int viewH, String title, String subtitle, Color accent) {
        g2.setColor(new Color(10, 12, 18, 175));
        g2.fillRect(0, HUD_HEIGHT, viewW, viewH - HUD_HEIGHT - FOOTER_HEIGHT);

        int boxW = 480;
        int boxH = 150;
        int bx = (viewW - boxW) / 2;
        int by = (viewH - boxH) / 2;

        g2.setColor(new Color(26, 32, 46));
        g2.fillRoundRect(bx, by, boxW, boxH, 16, 16);
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(bx, by, boxW, boxH, 16, 16);

        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        drawCenteredText(g2, title, viewW / 2, by + 58);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        drawCenteredText(g2, subtitle, viewW / 2, by + 102);
    }

    private void drawCenteredText(Graphics2D g2, String text, int centerX, int baselineY) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }

    // -------------------------------------------------------------------------
    // UserService / 파일 로더 완성 전 테스트용 샘플 스테이지 & 퀴즈
    // -------------------------------------------------------------------------

    public static Stage createDemoStage() {
        String[] rows = {
                "####################",
                "#..................#",
                "#..................#",
                "#.........####.....#",
                "#..................#",
                "#....####..........#",
                "#..............#...#",
                "#..............#...#",
                "#..............#...#",
                "####################"
        };
        int h = rows.length;
        int w = rows[0].length();
        Tile[][] terrain = new Tile[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                terrain[y][x] = Tile.fromChar(rows[y].charAt(x));
            }
        }

        List<GameObject> objects = List.of(
                new ItemObject("item_potion_1", 6, 4, ItemType.HEALTH_POTION, 1),
                new ButtonObject("btn_1", 11, 2, 1, List.of("door_1")),
                new DoorObject("door_1", 15, 6, 3),
                new KeyObject("key_1", 17, 8));

        return new Stage(
                1,
                "첫 번째 관문",
                "버튼 퀴즈를 풀고 잠긴 문을 열어 열쇠를 획득하세요!",
                StageType.NORMAL,
                1,
                terrain,
                2,
                8,
                objects);
    }

    public static Map<Integer, Quiz> createDemoQuizzes() {
        Map<Integer, Quiz> map = new HashMap<>();
        map.put(1, new ChoiceQuiz(
                1,
                1,
                "Java에서 배열의 길이를 구할 때 사용하는 속성은?",
                "배열은 length 필드를 사용하고, String은 length() 메서드를 사용합니다.",
                List.of("size()", "length", "count()", "getLength()"),
                2));
        map.put(2, new InputQuiz(
                2,
                1,
                "Java에서 상속을 선언할 때 사용하는 키워드는?",
                "클래스 상속에는 extends 키워드를 사용합니다.",
                "extends"));
        return map;
    }
}