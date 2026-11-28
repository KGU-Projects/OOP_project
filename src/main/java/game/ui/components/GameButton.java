package game.ui.components;

import game.audio.AudioManager;
import game.audio.AudioManager.Sound;
import game.ui.ImageLoader;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class GameButton extends JButton {

    private static final ImageLoader DEFAULT_LOADER = new ImageLoader();
    private final BufferedImage backgroundImage;
    private final boolean drawText;

    public GameButton(String text, Runnable onClick) {
        this(text, (BufferedImage) null, true, onClick);
    }

    public GameButton(String text, String imageFileName, Runnable onClick) {
        this(text, imageFileName, true, onClick);
    }

    public GameButton(String text, String imageFileName, boolean drawText, Runnable onClick) {
        this(text, loadImage(imageFileName), drawText, onClick);
    }

    public GameButton(String text, BufferedImage backgroundImage, boolean drawText, Runnable onClick) {
        super(text);
        this.backgroundImage = backgroundImage;
        this.drawText = drawText;
        setPreferredSize(new Dimension(240, 50));
        setFont(new Font("SansSerif", Font.BOLD, 16));
        setFocusPainted(false);
        if (this.backgroundImage != null) {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
        } else {
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(100, 100, 120), 1, true),
                    new EmptyBorder(5, 15, 5, 15)));
        }
        if (onClick != null) {
            addActionListener(e -> {
                AudioManager.getInstance().play(Sound.BUTTON);
                onClick.run();
            });
        }
    }

    private static BufferedImage loadImage(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        return DEFAULT_LOADER.get(fileName).orElse(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (backgroundImage == null) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int pressOffset = getModel().isPressed() ? 2 : 0;
        g2.drawImage(backgroundImage, 0, pressOffset, w, h - pressOffset, this);
        if (!isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, w, h);
        } else if (getModel().isPressed()) {
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillRect(0, pressOffset, w, h - pressOffset);
        } else if (getModel().isRollover()) {
            g2.setColor(new Color(255, 255, 255, 35));
            g2.fillRect(0, 0, w, h);
        }
        if (drawText && getText() != null && !getText().isEmpty()) {
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + pressOffset;
            g2.drawString(getText(), tx, ty);
        }
        g2.dispose();
    }
}
