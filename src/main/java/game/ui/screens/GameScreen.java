package game.ui.screens;

import game.common.ScreenType;
import game.model.AnswerResult;
import game.model.GameState;
import game.model.ItemType;
import game.model.User;
import game.model.object.GameObject;
import game.model.quiz.Quiz;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.Camera;
import game.ui.ScreenManager;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 메인 게임 화면.
 * 입력 처리, 40 x 20 카메라 추적, GameService 규칙 위임, 퀴즈 화면 전환을 조율한다.
 */
public class GameScreen extends BaseScreen {

    private static final int VIEWPORT_COLS = 40;
    private static final int VIEWPORT_ROWS = 20;
    private static final int FRAME_DELAY_MS = 16;
    private static final double FIXED_DT = 0.016;
    private static final int QUIZ_TIME_LIMIT_SECONDS = 20;

    private final UserService userService;
    private final GameService gameService;

    private final Camera camera;
    private final MapCanvas canvas;
    private final Timer gameLoop;

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

        panel.add(canvas, BorderLayout.CENTER);
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
                camera.snapTo(state.getPlayer(), state.getStage());
                showStatus("STAGE " + state.getStage().getNumber() + " - " + state.getStage().getTitle(), 2.0);
            }
        }
        if (!gameLoop.isRunning()) {
            gameLoop.start();
        }
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
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

    // ---------------------------------------------------------------------
    // 게임 루프
    // ---------------------------------------------------------------------

    private void onTick() {
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

    // ---------------------------------------------------------------------
    // 입력 바인딩
    // ---------------------------------------------------------------------

    private void setupKeyBindings() {
        InputMap im = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = canvas.getActionMap();

        bindMoveKey(im, am, KeyEvent.VK_LEFT, "left", (p, down) -> p.setLeftPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_A, "a", (p, down) -> p.setLeftPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_RIGHT, "right", (p, down) -> p.setRightPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_D, "d", (p, down) -> p.setRightPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_SPACE, "space", (p, down) -> p.setJumpPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_W, "w", (p, down) -> p.setJumpPressed(down));
        bindMoveKey(im, am, KeyEvent.VK_UP, "up", (p, down) -> p.setJumpPressed(down));

        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "interact", this::tryInteract);
        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_H, 0, false), "use_potion", this::tryUsePotion);
        bindAction(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape", this::handleEscape);
    }

    @FunctionalInterface
    private interface PlayerInputSetter {
        void apply(game.model.Player player, boolean pressed);
    }

    private void bindMoveKey(InputMap im, ActionMap am, int keyCode, String prefix, PlayerInputSetter setter) {
        bindAction(im, am, KeyStroke.getKeyStroke(keyCode, 0, false), prefix + "_down", () -> {
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

    // ---------------------------------------------------------------------
    // 상호작용 & 퀴즈 전투 연결
    // ---------------------------------------------------------------------

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
                // QuizBattleLauncher가 이미 GAME 화면으로 복귀시킨 뒤 호출된다.
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
        if (screenManager != null) {
            screenManager.show(ScreenType.START);
        }
    }
}