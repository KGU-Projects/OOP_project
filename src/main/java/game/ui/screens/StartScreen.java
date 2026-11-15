package game.ui.screens;

import game.common.ScreenType;
import game.service.UserService;
import game.ui.Screen;
import game.ui.ScreenManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

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
        JLabel titleLabel = new JLabel("CODE_ESCAPE");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 40));
        titleLabel.setForeground(new Color(255, 255, 255));

        // 패딩
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

        // startBtn.addActionListener();
        loadBtn.addActionListener(e -> screenManager.show(ScreenType.LOAD));
        // scoreboardBtn.addActionListener();
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

    // 버튼 메소드 이미지로 대체될 예정
    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(240, 50));
        btn.setFont(new Font("SansSerif", Font.BOLD, 16));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(100, 100, 120), 1, true),
                new EmptyBorder(5, 15, 5, 15)));
        return btn;
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