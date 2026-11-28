package game.ui.components;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

import javax.swing.JComponent;

/**
 * 손그림 PNG 한 장을 보여 주는 가벼운 컴포넌트. 투명 여백을 잘라 낸 그림을 비율을 지킨 채 칸 안에 맞추고
 * (halign, valign: 0=왼쪽·위, 0.5=가운데, 1=오른쪽·아래) 칸 안의 그 자리에 놓는다. 파일이 없으면 아무것도 그리지 않는다.
 */
public class SketchImage extends JComponent {

    private static final long serialVersionUID = 1L;

    private final transient SketchAssets assets;
    private String file;
    private float alpha = 1f;
    private final float halign;
    private final float valign;

    public SketchImage(SketchAssets assets, String file, float halign, float valign) {
        this.assets = assets;
        this.file = file;
        this.halign = halign;
        this.valign = valign;
        setOpaque(false);
        setFocusable(false);
    }

    public void setFile(String file) {
        this.file = file;
        repaint();
    }

    public String getFile() {
        return file;
    }

    /** 그림의 불투명도 (1 = 그대로, 낮추면 흐려진다: 멈춘 불꽃 표시 등) */
    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
        repaint();
    }

    public float getAlpha() {
        return alpha;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (file == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double aspect = assets.aspect(file);
        int dw = getWidth();
        int dh = (int) Math.round(dw / aspect);
        if (dh > getHeight()) {
            dh = getHeight();
            dw = (int) Math.round(dh * aspect);
        }
        Optional<BufferedImage> img = assets.scaled(file, Math.max(1, dw), Math.max(1, dh));
        if (img.isPresent()) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (alpha < 1f) {
                g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(alpha));
            }
            g2.drawImage(img.get(), Math.round((getWidth() - dw) * halign), Math.round((getHeight() - dh) * valign), null);
            g2.dispose();
        }
    }
}
