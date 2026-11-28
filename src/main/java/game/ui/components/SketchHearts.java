package game.ui.components;

import game.ui.GameFonts;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Optional;

import javax.swing.JComponent;

/**
 * "HP" 글자와 체력만큼 채워진 하트(hp_full.png), 잃은 만큼의 빈 하트(hp_empty.png)를 한 줄로 그린다.
 *
 * 줄 전체(글자 + 하트들)는 컴포넌트 가운데에 놓이고, 하트는 모두 같은 크기·같은 간격이다
 * (빨간 하트와 빈 하트는 같은 배율로 줄이고 칸 가운데에 놓아 서로 크기가 다르게 보이지 않는다).
 * 최대 HP가 많아 하트로 다 그릴 수 없으면(MAX_ICONS 초과) 하트 하나와 숫자로 보인다.
 */
public class SketchHearts extends JComponent {

    private static final long serialVersionUID = 1L;
    /** 하트 그림으로 그릴 수 있는 최대 개수 */
    public static final int MAX_ICONS = 8;
    private static final Color INK = new Color(20, 20, 20);

    private final transient SketchAssets assets;
    private final String label;
    private int hp;
    private int maxHp;

    public SketchHearts(SketchAssets assets, String label) {
        this.assets = assets;
        this.label = label;
        setOpaque(false);
        setFocusable(false);
    }

    public void setHp(int hp, int maxHp) {
        this.maxHp = Math.max(0, maxHp);
        this.hp = Math.max(0, Math.min(hp, this.maxHp));
        repaint();
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /** 채워진 하트 개수(그림으로 그릴 때). 하트로 다 그릴 수 없으면 숫자 표시라 -1. */
    public int visibleFullHearts() {
        return maxHp > 0 && maxHp <= MAX_ICONS ? hp : -1;
    }

    private Font labelFont() {
        return GameFonts.hand((float) (getFont().getSize2D() > 1 ? getFont().getSize2D() : getHeight() * 0.62));
    }

    /** 하트 한 칸의 크기: 컴포넌트 높이의 90%, 가로세로 비율은 빨간 하트 기준 */
    private int cellH() {
        return (int) Math.round(getHeight() * 0.90);
    }

    private int cellW() {
        return (int) Math.round(cellH() * assets.aspect(SketchAssets.HP_FULL));
    }

    private int pitch() {
        return (int) Math.round(cellW() * 1.25);
    }

    /** 줄 전체(글자 + 하트)가 차지하는 폭 (현재 크기 기준) */
    public int neededWidth() {
        if (getHeight() <= 0) {
            return 0;
        }
        FontMetrics fm = getFontMetrics(labelFont());
        int icons = Math.min(maxHp, MAX_ICONS);
        return fm.stringWidth(label) + (int) Math.round(getHeight() * 0.28) + Math.max(0, icons - 1) * pitch() + cellW();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int h = getHeight();
        if (h <= 0 || getWidth() <= 0 || maxHp <= 0) {
            return; // 최대 HP를 모르면(게임 로직이 아직 몬스터 HP를 주지 않는 경우) HP 줄을 그리지 않는다
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setFont(labelFont());
        g2.setColor(INK);
        FontMetrics fm = g2.getFontMetrics();
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();

        int cellH = cellH();
        int cellW = cellW();
        int pitch = pitch();
        int gap = (int) Math.round(h * 0.28);
        int icons = Math.min(maxHp, MAX_ICONS);
        int total = fm.stringWidth(label) + gap + Math.max(0, icons - 1) * pitch + cellW;
        if (maxHp > MAX_ICONS) {
            total = fm.stringWidth(label) + gap + cellW + (int) Math.round(h * 0.2) + fm.stringWidth(hp + "/" + maxHp);
        }
        int x0 = Math.max(0, (getWidth() - total) / 2);
        g2.drawString(label, x0, baseline);

        // 빨간 하트와 빈 하트를 같은 배율로 줄인다 (원본 비율 유지), 칸 가운데에 놓는다
        Optional<BufferedImage> fullSrc = assets.trimmed(SketchAssets.HP_FULL);
        Optional<BufferedImage> emptySrc = assets.trimmed(SketchAssets.HP_EMPTY);
        double s = fullSrc.map(i -> cellH / (double) i.getHeight()).orElse(1.0);
        int top = (h - cellH) / 2;
        int x = x0 + fm.stringWidth(label) + gap;
        Optional<BufferedImage> full = fullSrc.flatMap(i -> assets.scaled(SketchAssets.HP_FULL, cellW, cellH));
        Optional<BufferedImage> empty = emptySrc.flatMap(i -> assets.scaled(SketchAssets.HP_EMPTY,
                Math.max(1, (int) Math.round(i.getWidth() * s)), Math.max(1, (int) Math.round(i.getHeight() * s))));
        if (maxHp > MAX_ICONS) {
            final int fx = x;
            full.ifPresent(i -> g2.drawImage(i, fx, top, null));
            g2.drawString(hp + "/" + maxHp, x + cellW + (int) Math.round(h * 0.2), baseline);
        } else {
            for (int i = 0; i < maxHp; i++) {
                Optional<BufferedImage> img = i < hp ? full : empty;
                final int cellX = x + i * pitch;
                img.ifPresent(im -> g2.drawImage(im, cellX + (cellW - im.getWidth()) / 2, top + (cellH - im.getHeight()) / 2, null));
            }
        }
        g2.dispose();
    }
}
