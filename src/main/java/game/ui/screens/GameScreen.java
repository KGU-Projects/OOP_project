package game.ui.screens;

import game.common.ScreenType;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.SaveData;
import game.model.User;
import game.model.object.GameObject;
import game.model.quiz.Quiz;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.Camera;
import game.ui.ScreenManager;
import game.ui.components.GameButton;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameScreen extends BaseScreen {

    private static final int VIEWPORT_COLS = 40;
    private static final int VIEWPORT_ROWS = 20;
    private static final int FRAME_DELAY_MS = 16;
    private static final double FIXED_DT = 0.016;
    private static final int QUIZ_TIME_LIMIT_SECONDS = 20;
    private static final int PAUSE_DRAWER_WIDTH = 340;
    private static final Color BG = Color.BLACK;
    private static final Color FG = Color.WHITE;
    private static final DateTimeFormatter SAVE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserService userService;
    private final GameService gameService;

    private final Camera camera;
    private final MapCanvas canvas;
    private final Timer gameLoop;

    private final PauseOverlayPanel pauseOverlay;
    private final CardLayout pauseCardLayout = new CardLayout();
    private final JPanel pauseCardContainer = new JPanel(pauseCardLayout);
    private final JPanel saveSlotListPanel = new JPanel();
    private final JLabel pauseFeedbackLabel = new JLabel(" ");
    private boolean paused = false;

    private GameState lastObservedState;
    private QuizBattleLauncher quizLauncher;

    private String statusText = "";
    private double statusTimer = 0.0;

    public GameScreen(ScreenManager screenManager, UserService userService, GameService gameService) {
        super(screenManager, new BorderLayout());
        this.userService = userService;
        this.gameService = gameService;

        this.camera = new Camera(VIEWPORT_COLS, VIEWPORT_ROWS);
        this.canvas = new MapCanvas(
                camera,
                ObjectRendererRegistry.createMonochromeDefault(),
                this::currentState,
                this::findNearbyInteractable,
                this::currentStatusText);

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
                setPaused(false);
                camera.snapTo(state.getPlayer(), state.getStage());
                showStatus("STAGE " + state.getStage().getNumber() + " - " + state.getStage().getTitle(), 2.0);
            }
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
            gameService.startNewGame(User.createNew("Player", LocalDateTime.now()));
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

        JPanel leftDrawer = new JPanel(new BorderLayout(0, 18));
        leftDrawer.setPreferredSize(new Dimension(PAUSE_DRAWER_WIDTH, 0));
        leftDrawer.setOpaque(true);
        leftDrawer.setBackground(BG);
        leftDrawer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 0, 2, FG),
                new EmptyBorder(36, 28, 32, 28)));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 8));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, FG),
                new EmptyBorder(0, 0, 12, 0)));

        JLabel titleLabel = new JLabel("PAUSED");
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        titleLabel.setForeground(FG);
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        pauseFeedbackLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        pauseFeedbackLabel.setForeground(FG);
        headerPanel.add(pauseFeedbackLabel, BorderLayout.SOUTH);
        leftDrawer.add(headerPanel, BorderLayout.NORTH);

        pauseCardContainer.setOpaque(false);
        pauseCardContainer.add(createPauseMainButtons(), "MAIN");
        pauseCardContainer.add(createPauseSavePicker(), "SAVE");
        leftDrawer.add(pauseCardContainer, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel("[ESC] RESUME");
        hintLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        hintLabel.setForeground(FG);
        leftDrawer.add(hintLabel, BorderLayout.SOUTH);

        overlay.add(leftDrawer, BorderLayout.WEST);
        return overlay;
    }

    private JPanel createPauseMainButtons() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        JPanel buttonStack = new JPanel(new GridLayout(4, 1, 0, 14));
        buttonStack.setOpaque(false);

        buttonStack.add(stylePauseButton(new GameButton("계속하기 (RESUME)", () -> setPaused(false))));
        buttonStack.add(stylePauseButton(new GameButton("저장하기 (SAVE)", this::openSavePicker)));
        buttonStack.add(stylePauseButton(new GameButton("불러오기 (LOAD)", this::openLoadFromPause)));
        buttonStack.add(stylePauseButton(new GameButton("나가기 (EXIT)", this::exitToTitle)));

        wrapper.add(buttonStack, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel createPauseSavePicker() {
        JPanel picker = new JPanel(new BorderLayout(0, 12));
        picker.setOpaque(false);

        JLabel subTitle = new JLabel("[SAVE SLOT SELECT]");
        subTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        subTitle.setForeground(FG);
        picker.add(subTitle, BorderLayout.NORTH);

        saveSlotListPanel.setLayout(new BoxLayout(saveSlotListPanel, BoxLayout.Y_AXIS));
        saveSlotListPanel.setOpaque(false);
        picker.add(saveSlotListPanel, BorderLayout.CENTER);

        GameButton backBtn = stylePauseButton(new GameButton("뒤로 (BACK)", () -> {
            pauseFeedbackLabel.setText(" ");
            pauseCardLayout.show(pauseCardContainer, "MAIN");
        }));
        picker.add(backBtn, BorderLayout.SOUTH);

        return picker;
    }

    private GameButton stylePauseButton(GameButton btn) {
        btn.setPreferredSize(new Dimension(PAUSE_DRAWER_WIDTH - 56, 48));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        btn.setBackground(BG);
        btn.setForeground(FG);
        btn.setRolloverEnabled(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(FG, 2),
                new EmptyBorder(5, 14, 5, 14)));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(FG);
                    btn.setForeground(BG);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG);
                btn.setForeground(FG);
            }
        });
        return btn;
    }

    private void openSavePicker() {
        refreshSaveSlotButtons();
        pauseCardLayout.show(pauseCardContainer, "SAVE");
    }

    private void refreshSaveSlotButtons() {
        saveSlotListPanel.removeAll();
        if (gameService == null) {
            return;
        }
        String nickname = gameService.getCurrentUser().getNickname();
        List<Optional<SaveData>> slots = gameService.getAllSaveSlots(nickname);

        for (int i = 0; i < slots.size(); i++) {
            final int slotNo = i + 1;
            Optional<SaveData> dataOpt = slots.get(i);
            String label;
            if (dataOpt.isPresent()) {
                SaveData d = dataOpt.get();
                label = String.format("SLOT %d | STG %d (%s)",
                        slotNo, d.getNextStageNo(), d.getSavedAt().format(SAVE_TIME_FMT));
            } else {
                label = String.format("SLOT %d | [EMPTY]", slotNo);
            }

            if (i > 0) {
                saveSlotListPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            }
            GameButton slotBtn = stylePauseButton(new GameButton(label, () -> performSaveToSlot(slotNo)));
            slotBtn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            saveSlotListPanel.add(slotBtn);
        }
        saveSlotListPanel.revalidate();
        saveSlotListPanel.repaint();
    }

    private void performSaveToSlot(int slotNo) {
        if (gameService == null) {
            return;
        }
        try {
            gameService.saveGame(slotNo);
            pauseFeedbackLabel.setText(">> SLOT " + slotNo + " SAVED");
            showStatus("슬롯 " + slotNo + "에 저장되었습니다", 2.0);
            refreshSaveSlotButtons();
        } catch (RuntimeException ex) {
            pauseFeedbackLabel.setText(">> SAVE ERROR: " + ex.getMessage());
        }
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
            pauseFeedbackLabel.setText(" ");
            pauseCardLayout.show(pauseCardContainer, "MAIN");
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

        camera.follow(state.getPlayer(), state.getStage());
        canvas.repaint();
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
            // overlayLayer를 먼저 추가해야 Swing에서 최상단에 그려진다
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
}