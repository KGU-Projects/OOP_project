package game.ui.screens;

import game.audio.AudioSettingsDialog;
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

    private record MenuItem(String label, Runnable action) {
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
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 0);

        List<MenuItem> menuItems = List.of(
                new MenuItem("시작", () -> screenManager.show(ScreenType.GAME)),
                new MenuItem("불러오기", () -> screenManager.show(ScreenType.LOAD)),
                new MenuItem("오디오 설정", () -> AudioSettingsDialog.show(panel)),
                new MenuItem("종료", () -> System.exit(0)));

        JPanel buttonPanel = new JPanel(new GridLayout(menuItems.size(), 1, 0, 15));
        buttonPanel.setOpaque(false);
        for (MenuItem item : menuItems) {
            buttonPanel.add(new GameButton(item.label(), item.action()));
        }

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
