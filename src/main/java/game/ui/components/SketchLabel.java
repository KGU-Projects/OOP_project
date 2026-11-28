package game.ui.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JComponent;

/**
 * 칸에 맞춰 글자가 줄어드는 라벨. 글자는 칸 안에서 줄바꿈되고, 그래도 넘치면 글꼴을 줄이며(최소 minScale),
 * 마지막까지 안 들어가면 …으로 줄인다. 그래서 글자가 칸을 벗어나지 않는다. setFont로 정한 크기가 최대 크기다.
 */
public class SketchLabel extends JComponent {

    private static final long serialVersionUID = 1L;

    private String text;
    private boolean centered = true;
    private double minScale = 0.5;

    public SketchLabel(String text, Color color) {
        this.text = text == null ? "" : text;
        setForeground(color);
        setOpaque(false);
        setFocusable(false);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        repaint();
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
        repaint();
    }

    public void setMinScale(double minScale) {
        this.minScale = minScale;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (text.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        SketchText.Fitted fit = SketchText.fit(g2, text, getFont(), getWidth(), getHeight(), minScale);
        g2.setFont(fit.font());
        g2.setColor(isEnabled() ? getForeground() : new Color(150, 148, 146));
        List<String> lines = fit.lines();
        int total = lines.size() * fit.lineHeight();
        int y = (getHeight() - total) / 2 + g2.getFontMetrics().getAscent();
        for (String line : lines) {
            int x = centered ? (getWidth() - g2.getFontMetrics().stringWidth(line)) / 2 : 0;
            g2.drawString(line, x, y);
            y += fit.lineHeight();
        }
        g2.dispose();
    }
}
