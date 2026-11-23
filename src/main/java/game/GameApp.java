package game;

import game.common.GameConstants;
import game.common.ScreenType;
import game.repository.CutsceneRepository;
import game.repository.file.FileCutsceneRepository;
import game.repository.file.FileQuizRepository;
import game.repository.file.FileSaveRepository;
import game.repository.file.FileStageRepository;
import game.repository.file.FileUserRepository;

import java.nio.file.Path;

import java.awt.CardLayout;
//import java.awt.Dimension;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.ScreenManager;
import game.ui.screens.CutsceneScreen;
import game.ui.screens.GameScreen;
import game.ui.screens.LoadScreen;
import game.ui.screens.QuizScreen;
import game.ui.screens.StartScreen;
import game.ui.ImageLoader;

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
            // 기존: UserService userService = new UserService(); => 유저서비스 파일 완성해서 수정함.
            // users.txt를 읽고 쓰는 사용자 창고(FileUserRepository)를 만들어 UserService에 넘김.
            UserService userService = new UserService(new FileUserRepository(Path.of("data/users.txt")));
            GameService gameService = new GameService(
                    new FileQuizRepository(Path.of(GameConstants.QUIZ_FILE)),
                    new FileStageRepository(Path.of(GameConstants.STAGE_DIRECTORY)),
                    new FileSaveRepository(Path.of(GameConstants.SAVE_DIRECTORY)));
            CutsceneRepository cutsceneRepository = new FileCutsceneRepository(
                    Path.of(GameConstants.CUTSCENE_DIRECTORY));
            ImageLoader imageLoader = new ImageLoader();

            // 스크린매니저에 각 화면 등록
            screenManager.register(ScreenType.START, new StartScreen(screenManager));
            screenManager.register(ScreenType.GAME, new GameScreen(screenManager, gameService));
            screenManager.register(ScreenType.QUIZ, new QuizScreen(screenManager));
            screenManager.register(ScreenType.LOAD, new LoadScreen(screenManager, gameService));
            screenManager.register(ScreenType.CUTSCENE,
                    new CutsceneScreen(screenManager, cutsceneRepository, imageLoader));
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