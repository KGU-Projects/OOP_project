package game;

import game.ui.ScreenManager;
import game.ui.screens.SlopPlaceHolderScreen;
import game.ui.screens.LoadScreen;
import game.ui.screens.StartScreen;
import game.service.UserService;
import game.common.ScreenType;

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
            UserService userService = new UserService();

            // 스크린매니저에 각 화면 등록, 등장
            screenManager.register(ScreenType.START, new StartScreen(screenManager));
            screenManager.register(ScreenType.LOAD, new LoadScreen(screenManager, userService));
            screenManager.register(ScreenType.GAME,
                    new SlopPlaceHolderScreen(screenManager, () -> screenManager.show(ScreenType.GAME)));
            frame.add(mainContainer);
            frame.setVisible(true);
            screenManager.show(ScreenType.START);

        });
    }
}