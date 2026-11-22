package game;

import game.ui.ScreenManager;
import game.ui.screens.SlopPlaceHolderScreen;
import game.ui.screens.GameScreen;
import game.ui.screens.LoadScreen;
import game.ui.screens.StartScreen;
import game.service.UserService;
import game.service.GameService;
import game.common.GameConstants;
import game.common.ScreenType;
import game.repository.file.FileQuizRepository;
import game.repository.file.FileSaveRepository;
import game.repository.file.FileStageRepository;
import game.repository.file.FileUserRepository;

import java.nio.file.Path;

import java.awt.CardLayout;
//import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

//게임의 시작점
//jframe으로 게임 윈도우 생성
//UserService, ScreenManager 시동

public class GameApp {

    // 종횡비고정 16:9
    // private static final double ASPECT_RATIO = 16.0 / 9.0;
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    // private static final int MIN_WIDTH = 800;
    // private static final int MIN_HEIGHT = 450;

    public static void main(String[] args) {
        initGame();
    }

    private static void initGame() {
        SwingUtilities.invokeLater(() -> {

            // 창 생성
            JFrame frame = new JFrame("OOP_project");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            frame.setResizable(false);
            // frame.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
            frame.setLocationRelativeTo(null);

            CardLayout cardLayout = new CardLayout();
            JPanel mainContainer = new JPanel(cardLayout);

            ScreenManager screenManager = new ScreenManager(mainContainer, cardLayout);
            // 기존: UserService userService = new UserService(); => 유저서비스 파일 완성해서 수정함.
            // users.txt를 읽고 쓰는 사용자 창고(FileUserRepository)를 만들어 UserService에 넘김.
            UserService userService = new UserService(new FileUserRepository(Path.of("data/users.txt")));
            GameService gameService = new GameService(
                    new FileQuizRepository(Path.of(GameConstants.QUIZ_FILE)),
                    new FileStageRepository(Path.of(GameConstants.STAGE_DIRECTORY)),
                    new FileSaveRepository(Path.of(GameConstants.SAVE_DIRECTORY)));
            // PhysicsEngine physicsEngine = new PhysicsEngine();

            // 스크린매니저에 각 화면 등록
            screenManager.register(ScreenType.START, new StartScreen(screenManager));
            screenManager.register(ScreenType.LOAD, new LoadScreen(screenManager, userService));
            screenManager.register(ScreenType.GAME, new GameScreen(screenManager, userService, gameService));
            screenManager.register(ScreenType.PLACEHOLDER,
                    new SlopPlaceHolderScreen(screenManager, () -> screenManager.show(ScreenType.GAME)));
            frame.add(mainContainer);

            // 시작화면 등장
            screenManager.show(ScreenType.START);
            frame.setVisible(true);

        });
    }
}