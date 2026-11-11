package main.java.game.ui.screens;

import main.java.game.service.UserService;
import main.java.game.ui.Screen;
import main.java.game.ui.ScreenManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class StartScreen implements Screen {

    private final JPanel panel;
    private final ScreenManager screenManager;
    private final UserService userService;

    public StartScreen(ScreenManager screenManager, UserService userService) {
        this.screenManager = screenManager;
        this.userService = userService;
        this.panel = new JPanel(new GridBagLayout());
        initUI();
    }

    private void initUI() {
        panel.setOpaque(true);
        panel.setBackground(new Color(0, 0, 0));
        panel.setBorder(new EmptyBorder(40, 50, 40, 50));

        GridBagConstraints gbc = new GridBagConstraints();

        // 좌상단 제목
        JLabel titleLabel = new JLabel("가제 맛있다");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 40));
        titleLabel.setForeground(new Color(240, 240, 240));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 30, 0);
        panel.add(titleLabel, gbc);

        // 좌측 버튼
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 0, 15));
        buttonPanel.setOpaque(false);

        JButton startBtn = createStyledButton("시작");
        JButton loadBtn = createStyledButton("불러오기");
        JButton scoreboardBtn = createStyledButton("점수판");
        JButton exitBtn = createStyledButton("종료");

        startBtn.addActionListener(e -> showPlaceholder("시작"));
        loadBtn.addActionListener(e -> showPlaceholder("불러오기"));
        scoreboardBtn.addActionListener(e -> showPlaceholder("점수판"));
        exitBtn.addActionListener(e -> System.exit(0));

        buttonPanel.add(startBtn);
        buttonPanel.add(loadBtn);
        buttonPanel.add(scoreboardBtn);
        buttonPanel.add(exitBtn);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(buttonPanel, gbc);

    }

    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(240, 50));
        btn.setFont(new Font("SansSerif", Font.BOLD, 16));
        btn.setFocusPainted(false);
        btn.setBackground(new Color(60, 60, 75));
        btn.setForeground(new Color(240, 240, 240));
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(100, 100, 120), 1, true),
                new EmptyBorder(5, 15, 5, 15)));
        return btn;
    }

    private void showPlaceholder(String featureName) {
        JOptionPane.showMessageDialog(panel, featureName + " 안 만들었어요", "으악",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    @Override
    public void onEnter() {
    }

    @Override
    public void onExit() {
    }
}