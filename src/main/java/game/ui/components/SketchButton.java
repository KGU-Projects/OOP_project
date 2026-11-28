package game.ui.components;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * 손그림 PNG를 외형으로 쓰는 실제 JButton. 기본 Swing 버튼 모양은 그리지 않는다.
 *
 * 이미지는 투명 여백을 잘라 낸 그림을 컴포넌트 전체에 맞춰 그리므로, 눈에 보이는 그림 영역과 클릭 영역이 같다.
 * 상태 표시: 마우스를 올리면 그림 안쪽이 연한 회색, 누르는 동안은 더 진한 회색, 키보드 포커스(Tab)가 있으면 안쪽에
 * 점선 테두리가 보인다. 이미지의 손그림 테두리는 그대로이고, 비활성이면 흐려진다.
 *
 * 글자는 글꼴 그대로 이미지 안쪽 칸에서 줄바꿈된다. {@link #setShrinkToFit(boolean)}이 true(기본)면 칸에 안 들어갈 때
 * 글꼴을 줄이고, false면 글꼴 크기를 바꾸지 않는다(같은 문제의 보기끼리 글자 크기를 맞출 때).
 */
public class SketchButton extends JButton {

    private static final long serialVersionUID = 1L;

    private static final Color INK = new Color(20, 20, 20);
    private static final Color HOVER_FILL = new Color(0, 0, 0, 22);
    private static final Color PRESSED_FILL = new Color(0, 0, 0, 52);
    private static final Color FOCUS_RING = new Color(60, 60, 60);
    /** 번호 칸 폭 = 글꼴 크기 × 이 값 */
    public static final double LEADING_RATIO = 1.15;

    private final transient SketchAssets assets;
    private String imageFile;
    private Color labelColor = INK;
    private boolean shrinkToFit = true;
    private final double minScale = 0.45;
    private double padXRatioOverride = -1;
    private double padYRatioOverride = -1;
    /** 왼쪽에 작게 표시하는 번호(객관식 보기 1~4). 없으면 null */
    private String leadingLabel;
    private Color leadingColor = new Color(95, 93, 90);
    /** 제출 직후 채점 결과 색 (정답: 연한 초록, 오답: 연한 빨강). 없으면 null */
    private Color feedbackColor;

    public SketchButton(SketchAssets assets, String imageFile, String text, Runnable onClick) {
        super(text == null ? "" : text);
        this.assets = assets;
        this.imageFile = imageFile;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setFocusable(true);
        setRequestFocusEnabled(false); // 마우스로 눌러도 포커스 표시는 생기지 않고, Tab으로만 포커스가 온다
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Space는 JButton 기본 동작, Enter도 포커스가 있을 때 누른 것으로 처리한다
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sketchClick");
        getActionMap().put("sketchClick", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) {
                    doClick();
                }
            }
        });
        if (onClick != null) {
            addActionListener(e -> onClick.run());
        }
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
        repaint();
    }

    public String getImageFile() {
        return imageFile;
    }

    public void setLabelColor(Color color) {
        this.labelColor = color;
        repaint();
    }

    /** true(기본): 글자가 칸에 안 들어가면 글꼴을 줄인다. false: 글꼴 크기를 그대로 두고 줄바꿈만 한다. */
    public void setShrinkToFit(boolean shrinkToFit) {
        this.shrinkToFit = shrinkToFit;
        repaint();
    }

    /** 글자 칸의 안쪽 여백 비율(너비·높이 대비). 음수면 이미지 모양에 맞는 기본값. */
    public void setPaddingRatio(double padX, double padY) {
        this.padXRatioOverride = padX;
        this.padYRatioOverride = padY;
        repaint();
    }

    /** 버튼 왼쪽에 보기 번호를 작게 표시한다 (null이면 없음). 본문 글자 칸은 번호 폭만큼 줄어든다. */
    public void setLeadingLabel(String label) {
        this.leadingLabel = label == null || label.isBlank() ? null : label;
        repaint();
    }

    public String getLeadingLabel() {
        return leadingLabel;
    }

    /** 번호 칸의 폭(px): 현재 글꼴 크기에 비례해 모든 보기가 같다. 번호가 없으면 0 */
    public int leadingWidth() {
        return leadingLabel == null ? 0 : (int) Math.round(getFont().getSize2D() * LEADING_RATIO);
    }

    /** 채점 결과 색을 칠한다 (null이면 해제). 비활성 상태에서도 흐려지지 않고 색이 보인다. */
    public void setFeedbackColor(Color color) {
        this.feedbackColor = color;
        repaint();
    }

    public Color getFeedbackColor() {
        return feedbackColor;
    }

    /** 글자 칸의 가로 여백 비율: 굵은 테두리의 정사각형 모양 버튼은 가로로 긴 버튼보다 안쪽이 좁다. */
    public double padXRatio() {
        if (padXRatioOverride >= 0) {
            return padXRatioOverride;
        }
        return imageFile != null && assets.aspect(imageFile) < 6.0 ? 0.11 : 0.045;
    }

    public double padYRatio() {
        if (padYRatioOverride >= 0) {
            return padYRatioOverride;
        }
        return imageFile != null && assets.aspect(imageFile) < 6.0 ? 0.12 : 0.09;
    }

    private SketchText.Fitted fitText(Graphics2D g2, String text, int w, int h) {
        int padX = (int) Math.round(w * padXRatio());
        int padY = (int) Math.round(h * padYRatio());
        int tw = Math.max(1, w - padX * 2 - leadingWidth());
        int th = Math.max(1, h - padY * 2);
        if (shrinkToFit) {
            return SketchText.fit(g2, text, getFont(), tw, th, minScale);
        }
        FontMetrics fm = g2.getFontMetrics(getFont());
        return new SketchText.Fitted(getFont(), SketchText.wrap(text, fm, tw), fm.getHeight());
    }

    private static Graphics2D scratch() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    /**
     * 글자가 칸에 다 들어가지 못하는지(…으로 줄어들거나, 글꼴을 줄이지 않는 모드에서 칸 높이를 넘는 경우). 현재 크기·글꼴 기준.
     * 화면 구성 검증용이다.
     */
    public boolean isTextTruncated() {
        String text = getText();
        if (text == null || text.isBlank() || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        Graphics2D g2 = scratch();
        try {
            SketchText.Fitted fit = fitText(g2, text, getWidth(), getHeight());
            if (!shrinkToFit) {
                int th = getHeight() - 2 * (int) Math.round(getHeight() * padYRatio());
                return fit.lines().size() * fit.lineHeight() > th + 1;
            }
            if (text.contains("…")) {
                return false;
            }
            return fit.lines().stream().anyMatch(l -> l.endsWith("…"));
        } finally {
            g2.dispose();
        }
    }

    /** 글자가 줄바꿈되어 몇 줄로 보이는지 (현재 크기·글꼴 기준, 검증용) */
    public int textLineCount() {
        return fittedLines().size();
    }

    /** 실제로 그려지는 글자 줄들 (검증용) */
    public List<String> fittedLines() {
        String text = getText();
        if (text == null || text.isBlank() || getWidth() <= 0 || getHeight() <= 0) {
            return List.of();
        }
        Graphics2D g2 = scratch();
        try {
            return fitText(g2, text, getWidth(), getHeight()).lines();
        } finally {
            g2.dispose();
        }
    }

    /** 실제로 그려지는 글꼴 (글꼴을 줄이는 모드에서는 줄어든 크기, 검증용) */
    public Font fittedFont() {
        String text = getText();
        if (text == null || text.isBlank() || getWidth() <= 0 || getHeight() <= 0) {
            return getFont();
        }
        Graphics2D g2 = scratch();
        try {
            return fitText(g2, text, getWidth(), getHeight()).font();
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        boolean on = isEnabled();
        boolean pressed = on && getModel().isPressed() && getModel().isArmed();
        boolean hover = on && !pressed && getModel().isRollover();
        if (!on && feedbackColor == null) {
            g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        }

        // 손그림 테두리 안쪽에 연한 회색 바닥 (이미지는 안쪽이 투명하다): 올림 → 연한 회색, 누름 → 더 진한 회색
        if (pressed || hover) {
            int inset = Math.max(3, Math.round(Math.min(w, h) * 0.07f));
            double arc = Math.min(h - inset * 2, h * 0.7);
            g2.setColor(pressed ? PRESSED_FILL : HOVER_FILL);
            g2.fill(new RoundRectangle2D.Double(inset, inset, w - inset * 2, h - inset * 2, arc, arc));
        }

        if (feedbackColor != null) {
            int inset = Math.max(3, Math.round(Math.min(w, h) * 0.07f));
            double arc = Math.min(h - inset * 2, h * 0.7);
            g2.setColor(feedbackColor);
            g2.fill(new RoundRectangle2D.Double(inset, inset, w - inset * 2, h - inset * 2, arc, arc));
        }

        Optional<BufferedImage> img = imageFile == null ? Optional.empty() : assets.scaled(imageFile, w, h);
        img.ifPresent(i -> g2.drawImage(i, 0, 0, null));

        // 키보드 포커스: 안쪽 점선 테두리
        if (on && isFocusOwner()) {
            int inset = Math.max(5, Math.round(Math.min(w, h) * 0.13f));
            double arc = Math.min(h - inset * 2, h * 0.6);
            g2.setColor(FOCUS_RING);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {4f, 4f}, 0f));
            g2.draw(new RoundRectangle2D.Double(inset, inset, w - inset * 2 - 1, h - inset * 2 - 1, arc, arc));
            g2.setStroke(new BasicStroke(1f));
        }

        if (pressed) {
            g2.translate(1, 1);
        }
        String text = getText();
        if (text != null && !text.isBlank()) {
            int padX = (int) Math.round(w * padXRatio()) + leadingWidth();
            int padY = (int) Math.round(h * padYRatio());
            int tw = Math.max(1, w - (int) Math.round(w * padXRatio()) * 2 - leadingWidth());
            int th = Math.max(1, h - padY * 2);
            SketchText.Fitted fit = fitText(g2, text, w, h);
            g2.setFont(fit.font());
            g2.setColor(labelColor);
            List<String> lines = fit.lines();
            int total = lines.size() * fit.lineHeight();
            int y = padY + (th - total) / 2 + g2.getFontMetrics().getAscent();
            for (String line : lines) {
                int x = padX + (tw - g2.getFontMetrics().stringWidth(line)) / 2;
                g2.drawString(line, x, y);
                y += fit.lineHeight();
            }
        }
        g2.dispose();
        paintLeading(g, w, h, on);
    }

    /** 번호: 글자 칸 왼쪽 끝에 본문보다 작고 연한 글씨로 (눌린 상태의 이동은 따르지 않는다) */
    private void paintLeading(Graphics g, int w, int h, boolean on) {
        if (leadingLabel == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        if (!on && feedbackColor == null) {
            g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        }
        Font f = getFont().deriveFont(Font.PLAIN, (float) (getFont().getSize2D() * 0.78));
        g2.setFont(f);
        g2.setColor(leadingColor);
        FontMetrics fm = g2.getFontMetrics();
        int x = (int) Math.round(w * padXRatio());
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(leadingLabel, x, y);
        g2.dispose();
    }
}
