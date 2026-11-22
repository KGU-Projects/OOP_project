package game;

import game.common.GameConstants;
import game.common.ScreenType;
import game.repository.file.FileQuizRepository;
import game.repository.file.FileSaveRepository;
import game.repository.file.FileStageRepository;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.ScreenManager;
import game.ui.screens.GameScreen;
import game.ui.screens.LoadScreen;
import game.ui.screens.QuizScreen;
import game.ui.screens.StartScreen;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.nio.file.Path;

// 게임의 시작점
// JFrame으로 게임 윈도우 생성
// UserService, GameService, ScreenManager 시동
public class GameApp {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    public static void main(String[] args) {
        initGame();
    }

    private static void initGame() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CODE ESCAPE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);

            CardLayout cardLayout = new CardLayout();
            JPanel mainContainer = new JPanel(cardLayout);

            ScreenManager screenManager = new ScreenManager(mainContainer, cardLayout);
            UserService userService = new UserService();
            GameService gameService = new GameService(
                    new FileQuizRepository(Path.of(GameConstants.QUIZ_FILE)),
                    new FileStageRepository(Path.of(GameConstants.STAGE_DIRECTORY)),
                    new FileSaveRepository(Path.of(GameConstants.SAVE_DIRECTORY)));

            // 스크린매니저에 각 화면 등록
            screenManager.register(ScreenType.START, new StartScreen(screenManager));
            screenManager.register(ScreenType.LOAD, new LoadScreen(screenManager, userService));
            screenManager.register(ScreenType.GAME, new GameScreen(screenManager, userService, gameService));
            screenManager.register(ScreenType.QUIZ, new QuizScreen(screenManager));
            screenManager.register(ScreenType.PLACEHOLDER, new BaseScreen(screenManager, new GridBagLayout()) {
                @Override
                public void onEnter() {
                    screenManager.show(ScreenType.GAME);
                }
            });

            frame.add(mainContainer);

            // 시작화면 등장
            screenManager.show(ScreenType.START);
            frame.setVisible(true);
        });
    }
}