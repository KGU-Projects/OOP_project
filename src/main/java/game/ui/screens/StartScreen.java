package game.ui.screens;

import game.common.ScreenType;
//import game.service.UserService;
import game.ui.BaseScreen;
//import game.ui.Screen;
import game.ui.ScreenManager;
//import game.ui.components.GameButton;
import game.ui.components.GameButton;

//import javax.swing.BorderFactory;
//import javax.swing.JButton;
//import javax.swing.JLabel;
//import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

public class StartScreen extends BaseScreen {

    // private final JPanel panel;
    // private final ScreenManager screenManager;
    // private final UserService userService;

    private record MenuItem(String label, Runnable action) {
    }

    // public StartScreen(ScreenManager screenManager, UserService userService) {
    public StartScreen(ScreenManager screenManager) {
        super(screenManager, new GridBagLayout());
        // this.screenManager = screenManager;
        // this.userService = userService;
        // this.panel = new JPanel(new GridBagLayout());
        initUI();
    }

    private void initUI() {
        /*
         * panel.setOpaque(true);
         * panel.setBackground(new Color(0, 0, 0));
         * panel.setBorder(new EmptyBorder(40, 50, 40, 50));
         */

        GridBagConstraints gbc = new GridBagConstraints();

        // 패딩
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 30, 0);

        // 좌상단 제목
        panel.add(createTitleLabel("CODE_ESCAPE"), gbc);

        // 좌측 버튼
        List<MenuItem> menuItems = List.of(
                new MenuItem("시작", () -> screenManager.show(ScreenType.GAME)), // 추후에 올바른 커넥터로
                new MenuItem("불러오기", () -> screenManager.show(ScreenType.LOAD)),
                new MenuItem("점수화면", () -> screenManager.show(ScreenType.PLACEHOLDER)),
                new MenuItem("종료", () -> System.exit(0)));

        JPanel buttonPanel = new JPanel(new GridLayout(menuItems.size(), 1, 0, 15));
        buttonPanel.setOpaque(false);
        for (MenuItem item : menuItems) {
            buttonPanel.add(new GameButton(item.label(), item.action()));
        }
        /*
         * 
         * // startBtn.addActionListener();
         * loadBtn.addActionListener(e -> screenManager.show(ScreenType.LOAD));
         * // scoreboardBtn.addActionListener();
         * exitBtn.addActionListener(e -> System.exit(0));
         * 
         * buttonPanel.add(startBtn);
         * buttonPanel.add(loadBtn);
         * buttonPanel.add(scoreboardBtn);
         * buttonPanel.add(exitBtn);
         */

        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(buttonPanel, gbc);
        panel.setBorder(new EmptyBorder(40, 50, 40, 50));

    }

    /*
     * 버튼 메소드 이미지로 대체될 예정
     * private JButton createStyledButton(String text) {
     * JButton btn = new JButton(text);
     * btn.setPreferredSize(new Dimension(240, 50));
     * btn.setFont(new Font("SansSerif", Font.BOLD, 16));
     * btn.setFocusPainted(false);
     * btn.setOpaque(true);
     * btn.setBorder(BorderFactory.createCompoundBorder(
     * new LineBorder(new Color(100, 100, 120), 1, true),
     * new EmptyBorder(5, 15, 5, 15)));
     * return btn;
     * }
     * 
     * @Override
     * public JPanel getPanel() {
     * return panel;
     * }
     * 
     * @Override
     * public void onEnter() {
     * }
     * 
     * @Override
     * public void onExit() {
     * }
     */
}