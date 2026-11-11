package game;

import game.ui.ScreenManager;
import game.ui.screens.StartScreen;
import game.service.UserService;
import game.common.ScreenType;

import java.awt.CardLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

//게임의 시작점
//jframe으로 게임 윈도우 생성
//UserService, ScreenManager 시동

public class GameApp {

    public static void main(String[] args) {
        initGame();
    }

    private static void initGame() {
        SwingUtilities.invokeLater(() -> {
            // 창 jframe 생성, 설정
            JFrame frame = new JFrame("OOP_project");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 900);
            frame.setMinimumSize(new java.awt.Dimension(800, 600));
            frame.setLocationRelativeTo(null);

            CardLayout cardLayout = new CardLayout();
            JPanel mainContainer = new JPanel(cardLayout);

            ScreenManager screenManager = new ScreenManager(mainContainer, cardLayout);
            UserService userService = new UserService();

            screenManager.register(ScreenType.START, new StartScreen(screenManager, userService));
            frame.add(mainContainer);
            frame.setVisible(true);
            screenManager.show(ScreenType.START);

        });
    }
}