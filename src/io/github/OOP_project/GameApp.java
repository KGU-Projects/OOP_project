package io.github.OOP_project;

import io.github.OOP_project.ui.*;
import io.github.OOP_project.ui.screens.*;
import io.github.OOP_project.service.*;
import io.github.OOP_project.common.*;

import javax.swing.JFrame;
//import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.*;
//import java.lang.classfile.Label;

public class GameApp {

    void main() {
        initGame();
    }

    private void initGame() {
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