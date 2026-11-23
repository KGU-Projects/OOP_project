package game.ui.screens;

import game.common.ScreenType;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.object.GameObject;
import game.model.quiz.Quiz;
import game.service.GameService;
import game.ui.BaseScreen;
import game.ui.Camera;
import game.ui.ImageLoader;
import game.ui.ScreenManager;
import game.ui.components.GameButton;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameScreen extends BaseScreen {

    private static final int VIEWPORT_COLS = 40;
    private static final int VIEWPORT_ROWS = 20;
    private static final int FRAME_DELAY_MS = 16;
    private static final double FIXED_DT = 0.016;
    private static final int QUIZ_TIME_LIMIT_SECONDS = 20;
    private static final int PAUSE_DRAWER_WIDTH = 340;
    private static final String PLACEHOLDER_TEXTURE = "example-picture.png";
    private static final Color BG = Color.BLACK;
    private static final Color FG = Color.WHITE;

    private final GameService gameService;
    private final BufferedImage placeholderTexture;

    private final Camera camera;
    private final MapCanvas canvas;
    private final Timer gameLoop;

    private final PauseOverlayPanel pauseOverlay;
    private boolean paused = false;

    private GameState lastObservedState;
    private boolean stageClearTransitionScheduled = false;
    private QuizBattleLauncher quizLauncher;

    private String statusText = "";
    private double statusTimer = 0.0;

    public GameScreen(ScreenManager screenManager, GameService gameService) {
        this(screenManager, gameService, new ImageLoader());
    }

    public GameScreen(ScreenManager screenManager, GameService gameService, ImageLoader imageLoader) {
        super(screenManager, new BorderLayout());
        this.gameService = gameService;
        ImageLoader loader = (imageLoader != null) ? imageLoader : new ImageLoader();
        this.placeholderTexture = loader.get(PLACEHOLDER_TEXTURE).orElse(null);

        this.camera = new Camera(VIEWPORT_COLS, VIEWPORT_ROWS);
        this.canvas = new MapCanvas(
                camera,
                ObjectRendererRegistry.createTexturedDefault(placeholderTexture),
                this::currentState,
                this::findNearbyInteractable,
                this::currentStatusText,
                placeholderTexture);

        this.pauseOverlay = createPauseOverlay();

        LayeredGameContainer layeredRoot = new LayeredGameContainer(canvas, pauseOverlay);
        panel.add(layeredRoot, BorderLayout.CENTER);
        setupKeyBindings();

        this.gameLoop = new Timer(FRAME_DELAY_MS, e -> onTick());
    }

    @Override
    public void onEnter() {
        ensureGameStarted();
        GameState state = currentState();
        if (state != null) {
            state.getPlayer().clearInputs();
            if (state != lastObservedState) {
                lastObservedState = state;
                stageClearTransitionScheduled = false;
                setPaused(false);
                camera.snapTo(state.getPlayer(), state.getStage());

                Optional<String> cutscene = state.getStage().getCutsceneFile();
                if (cutscene.isPresent()
                        && screenManager != null
                        && screenManager.getScreen(ScreenType.CUTSCENE) instanceof CutsceneScreen cutsceneScreen) {
                    cutsceneScreen.play(cutscene.get(), () -> screenManager.show(ScreenType.GAME));
                    return;
                }
            }
            showStatus("STAGE " + state.getStage().getNumber() + " - " + state.getStage().getTitle(), 2.0);
        }
        if (!gameLoop.isRunning()) {
            gameLoop.start();
        }
        SwingUtilities.invokeLater(() -> {
            if (paused) {
                pauseOverlay.requestFocusInWindow();
            } else {
                canvas.requestFocusInWindow();
            }
        });
    }

    @Override
    public void onExit() {
        GameState state = currentState();
        if (state != null) {
            state.getPlayer().clearInputs();
        }
        if (gameLoop.isRunning()) {
            gameLoop.stop();
        }
    }

    private void ensureGameStarted() {
        if (gameService != null && gameService.getState() == null) {
            gameService.startNewGame();
        }
    }

    private GameState currentState() {
        return (gameService != null) ? gameService.getState() : null;
    }

    private Optional<GameObject> findNearbyInteractable() {
        return (gameService != null) ? gameService.findNearbyInteractable() : Optional.empty();
    }

    private String currentStatusText() {
        return (statusTimer > 0.0) ? statusText : "";
    }

    private void showStatus(String text, double durationSec) {
        this.statusText = text;
        this.statusTimer = durationSec;
    }

    private PauseOverlayPanel createPauseOverlay() {
        PauseOverlayPanel overlay = new PauseOverlayPanel();
        overlay.setVisible(false);

        TexturedPanel leftDrawer = new TexturedPanel(placeholderTexture, 175, new BorderLayout(0, 18));
        leftDrawer.setPreferredSize(new Dimension(PAUSE_DRAWER_WIDTH, 0));
        leftDrawer.setBorder(new EmptyBorder(36, 28, 32, 28));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel titleLabel = new JLabel("정지");
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        titleLabel.setForeground(FG);
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        leftDrawer.add(headerPanel, BorderLayout.NORTH);

        JPanel buttonWrapper = new JPanel(new BorderLayout());
        buttonWrapper.setOpaque(false);

        JPanel buttonStack = new JPanel(new GridLayout(3, 1, 0, 14));
        buttonStack.setOpaque(false);
        buttonStack.add(createPauseButton("계속하기", () -> setPaused(false)));
        buttonStack.add(createPauseButton("불러오기", this::openLoadFromPause));
        buttonStack.add(createPauseButton("나가기", this::exitToTitle));

        buttonWrapper.add(buttonStack, BorderLayout.NORTH);
        leftDrawer.add(buttonWrapper, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel("[ESC] 계속하기");
        hintLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        hintLabel.setForeground(FG);
        leftDrawer.add(hintLabel, BorderLayout.SOUTH);

        overlay.add(leftDrawer, BorderLayout.WEST);
        return overlay;
    }

    private GameButton createPauseButton(String label, Runnable action) {
        TexturedPauseButton btn = new TexturedPauseButton(label, action, placeholderTexture);
        btn.setPreferredSize(new Dimension(PAUSE_DRAWER_WIDTH - 56, 48));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        btn.setBackground(BG);
        btn.setForeground(FG);
        btn.setRolloverEnabled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(5, 14, 5, 14));
        return btn;
    }

    private void openLoadFromPause() {
        if (screenManager == null) {
            return;
        }
        if (screenManager.getScreen(ScreenType.LOAD) instanceof LoadScreen loadScreen) {
            loadScreen.setReturnScreen(ScreenType.GAME);
        }
        screenManager.show(ScreenType.LOAD);
    }

    private void exitToTitle() {
        setPaused(false);
        if (screenManager != null) {
            screenManager.show(ScreenType.START);
        }
    }

    private void setPaused(boolean value) {
        this.paused = value;
        GameState state = currentState();
        if (state != null) {
            state.getPlayer().clearInputs();
        }
        if (value) {
            pauseOverlay.setVisible(true);
            SwingUtilities.invokeLater(pauseOverlay::requestFocusInWindow);
        } else {
            pauseOverlay.setVisible(false);
            SwingUtilities.invokeLater(canvas::requestFocusInWindow);
        }
        panel.revalidate();
        panel.repaint();
    }

    private void onTick() {
        if (paused) {
            return;
        }
        GameState state = currentState();
        if (state == null) {
            return;
        }
        if (statusTimer > 0.0) {
            statusTimer = Math.max(0.0, statusTimer - FIXED_DT);
        }

        GameObject.InteractionOutcome outcome = gameService.updatePlayer(FIXED_DT);
        handleOutcome(outcome);

        if (state.isStageCleared() && !stageClearTransitionScheduled) {
            stageClearTransitionScheduled = true;
            int clearedStageNo = state.getStage().getNumber();
            int savedSlot = gameService.autoSaveOnStageClear();
            showStatus("STAGE CLEAR - 자동 저장 완료 (슬롯 " + savedSlot + ")", 1.5);

            Timer clearDelay = new Timer(1200, e -> advanceAfterStageClear(clearedStageNo));
            clearDelay.setRepeats(false);
            clearDelay.start();
        }

        camera.follow(state.getPlayer(), state.getStage());
        canvas.repaint();
    }

    private void advanceAfterStageClear(int clearedStageNo) {
        int nextStageNo = clearedStageNo + 1;
        if (gameService != null && gameService.hasStage(nextStageNo) && gameService.loadStage(nextStageNo)) {
            lastObservedState = null;
            screenManager.show(ScreenType.GAME);
            return;
        }
        if (screenManager != null) {
            screenManager.show(ScreenType.START);
        }
    }

    private void handleOutcome(GameObject.InteractionOutcome outcome) {
        if (outcome == null || outcome.type() == GameObject.EffectType.NONE) {
            return;
        }
        switch (outcome.type()) {
            case MESSAGE, STAGE_CLEAR -> showStatus(outcome.message(), 2.2);
            case START_QUIZ -> openQuizFor(outcome.source());
        }
    }

    private void setupKeyBindings() {
        InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();

        bindMoveKey(im, am, KeyEvent.VK_LEFT, "left", (p, down) -> p.setLeftPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_A, "a", (p, down) -> p.setLeftPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_RIGHT, "right", (p, down) -> p.setRightPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_D, "d", (p, down) -> p.setRightPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_SPACE, "space", (p, down) -> p.setJumpPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_W, "w", (p, down) -> p.setJumpPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_UP, "up", (p, down) -> p.setJumpPressed(down));

        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "interact", () -> {
            if (!paused) {
                tryInteract();
            }
        });
        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, false), "use_potion", () -> {
            if (!paused) {
                tryUsePotion();
            }
        });
        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape", this::handleEscape);
    }

    @FunctionalInterface
    private interface PlayerInputSetter {
        void apply(game.model.Player player, boolean pressed);
    }

    private void bindMoveKey(InputMap im, ActionMap am, int keyCode, String prefix, PlayerInputSetter setter) {
        bindAction(im, am, KeyStroke.getKeyStroke(keyCode, 0, false), prefix + "_down", () -> {
            if (paused) {
                return;
            }
            GameState state = currentState();
            if (state != null) {
                setter.apply(state.getPlayer(), true);
            }
        });
        bindAction(im, am, KeyStroke.getKeyStroke(keyCode, 0, true), prefix + "_up", () -> {
            GameState state = currentState();
            if (state != null) {
                setter.apply(state.getPlayer(), false);
            }
        });
    }

    private void bindAction(InputMap im, ActionMap am, KeyStroke stroke, String name, Runnable action) {
        im.put(stroke, name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void tryInteract() {
        findNearbyInteractable().ifPresent(target -> {
            GameObject.InteractionOutcome outcome = gameService.interact(target);
            handleOutcome(outcome);
        });
    }

    private void openQuizFor(GameObject source) {
        if (source == null) {
            return;
        }
        Optional<Quiz> opened = gameService.openQuiz(source);
        if (opened.isEmpty()) {
            return;
        }
        QuizBattleLauncher launcher = getOrCreateQuizLauncher();
        if (launcher == null) {
            return;
        }

        GameState state = currentState();
        if (state != null) {
            state.getPlayer().clearInputs();
        }

        AtomicBoolean initialConsumed = new AtomicBoolean(false);
        QuizBattleController.BattleRules rules = new GameServiceBattleRules<>(
                gameService::getState,
                () -> initialConsumed.compareAndSet(false, true) ? opened : gameService.getCurrentQuiz(),
                gameService::submitAnswer,
                this::toJudgement,
                gameService::cancelQuiz);

        launcher.startBattle(rules, "플레이어", source.getType().name(), QUIZ_TIME_LIMIT_SECONDS, this::onQuizBattleExit);
    }

    private QuizBattleController.Judgement toJudgement(AnswerResult r) {
        return new QuizBattleController.Judgement(
                r.isCorrect(),
                r.getDamageTaken(),
                r.getBossDamage(),
                r.isPlayerDead(),
                r.isCorrect() || r.isBossDefeated());
    }

    private void onQuizBattleExit(QuizBattleController.ExitReason reason) {
        switch (reason) {
            case RESTART_STAGE -> {
                if (gameService.canRestartFromSave() && gameService.restartFromSave()) {
                    lastObservedState = null;
                    screenManager.show(ScreenType.GAME);
                } else {
                    screenManager.show(ScreenType.START);
                }
            }
            case STAGE_SELECT -> screenManager.show(ScreenType.START);
            case VICTORY, NO_QUIZ, ABORTED, GO_RECOVER -> {
            }
        }
    }

    private QuizBattleLauncher getOrCreateQuizLauncher() {
        if (quizLauncher != null) {
            return quizLauncher;
        }
        if (screenManager != null && screenManager.getScreen(ScreenType.QUIZ) instanceof QuizScreen) {
            quizLauncher = new QuizBattleLauncher(screenManager);
        }
        return quizLauncher;
    }

    private void tryUsePotion() {
        if (gameService == null) {
            return;
        }
        if (gameService.useItem(ItemType.HEALTH_POTION)) {
            showStatus("체력 회복 (+1)", 1.5);
        } else {
            showStatus("물약을 사용할 수 없습니다", 1.2);
        }
    }

    private void handleEscape() {
        setPaused(!paused);
    }

    private static final class LayeredGameContainer extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JComponent baseLayer;
        private final JComponent overlayLayer;

        private LayeredGameContainer(JComponent baseLayer, JComponent overlayLayer) {
            super(null);
            this.baseLayer = baseLayer;
            this.overlayLayer = overlayLayer;
            add(overlayLayer);
            add(baseLayer);
        }

        @Override
        public boolean isOptimizedDrawingEnabled() {
            return !overlayLayer.isVisible();
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            baseLayer.setBounds(0, 0, w, h);
            overlayLayer.setBounds(0, 0, w, h);
        }
    }

    private static final class PauseOverlayPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private PauseOverlayPanel() {
            super(new BorderLayout());
            setOpaque(false);
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class TexturedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final BufferedImage texture;
        private final int overlayAlpha;

        private TexturedPanel(BufferedImage texture, int overlayAlpha, BorderLayout layout) {
            super(layout);
            this.texture = texture;
            this.overlayAlpha = overlayAlpha;
            setOpaque(true);
            setBackground(BG);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (texture != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(texture, 0, 0, getWidth(), getHeight(), this);
                g2.setColor(new Color(0, 0, 0, overlayAlpha));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        }
    }

    private static final class TexturedPauseButton extends GameButton {
        private static final long serialVersionUID = 1L;
        private final BufferedImage texture;
        private boolean hovered = false;

        private TexturedPauseButton(String text, Runnable action, BufferedImage texture) {
            super(text, action);
            this.texture = texture;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        setBackground(FG);
                        setForeground(BG);
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    setBackground(BG);
                    setForeground(FG);
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (texture == null) {
                super.paintComponent(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(texture, 0, 0, getWidth(), getHeight(), this);
            g2.setColor(hovered ? new Color(255, 255, 255, 200) : new Color(0, 0, 0, 145));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(getFont());
            g2.setColor(hovered ? BG : FG);
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int textX = (getWidth() - fm.stringWidth(text)) / 2;
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, textX, textY);
            g2.dispose();
        }
    }
}