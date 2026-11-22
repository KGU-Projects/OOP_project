package game.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
//import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.LayoutManager;

//screens에서 상속하는 클래스
//
public abstract class BaseScreen implements Screen {

    protected final JPanel panel;
    protected final ScreenManager screenManager;

    protected BaseScreen(ScreenManager screenManager, LayoutManager layout) {

        // 패널 스타일링
        this.screenManager = screenManager;
        this.panel = new JPanel(layout);
        this.panel.setOpaque(true);
        this.panel.setBackground(Color.BLACK);
    }

    // 타이틀, 추후 이미지로 대체할 것
    protected JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 40));
        label.setForeground(Color.WHITE);
        return label;
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