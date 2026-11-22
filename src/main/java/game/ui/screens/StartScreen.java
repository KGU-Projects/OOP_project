package game.ui.screens;

import game.common.ScreenType;
import game.ui.BaseScreen;
import game.ui.ImageLoader;
import game.ui.ScreenManager;
import game.ui.components.GameButton;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

public class StartScreen extends BaseScreen {

    private static final String BACKGROUND_IMAGE = "start-wallpaper.png";

    private record MenuItem(String label, String imageFile, boolean drawText, Runnable action) {
    }

    private final BufferedImage backgroundImage;

    public StartScreen(ScreenManager screenManager) {
        this(screenManager, new ImageLoader());
    }

    public StartScreen(ScreenManager screenManager, ImageLoader imageLoader) {
        super(screenManager, new BorderLayout());
        ImageLoader loader = (imageLoader != null) ? imageLoader : new ImageLoader();
        this.backgroundImage = loader.get(BACKGROUND_IMAGE).orElse(null);
        initUI();
    }

    private void initUI() {
        JPanel backgroundPanel = new BackgroundPanel(backgroundImage);
        backgroundPanel.setBorder(new EmptyBorder(40, 50, 40, 50));

        GridBagConstraints gbc = new GridBagConstraints();

        // 패딩
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 30, 0);

        // 좌측 버튼 (완성형 버튼 이미지면 drawText = false, 빈 틀 이미지 위에 글자를 얹으려면 true)
        List<MenuItem> menuItems = List.of(
                new MenuItem("시작", "startscreen-startbutton.png", false, () -> screenManager.show(ScreenType.GAME)),
                new MenuItem("불러오기", "startscreen-loadbutton.png", false, () -> screenManager.show(ScreenType.LOAD)),
                new MenuItem("종료", "startscreen-exitbutton.png", false, () -> System.exit(0)));

        JPanel buttonPanel = new JPanel(new GridLayout(menuItems.size(), 1, 0, 15));
        buttonPanel.setOpaque(false);
        for (MenuItem item : menuItems) {
            buttonPanel.add(new GameButton(item.label(), item.imageFile(), item.drawText(), item.action()));
        }

        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 0);
        backgroundPanel.add(buttonPanel, gbc);

        panel.add(backgroundPanel, BorderLayout.CENTER);
    }

    private static final class BackgroundPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private final BufferedImage image;

        private BackgroundPanel(BufferedImage image) {
            super(new GridBagLayout());
            this.image = image;
            setOpaque(true);
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), this);
                g2.dispose();
            }
        }
    }
}