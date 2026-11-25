package game.ui.components;

import game.common.GameConstants;
import game.model.GameState;
import game.model.Stage;
import game.model.StageType;
import game.model.object.GameObject;
import game.model.object.ObjectType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

/** 왼쪽 위 미니맵(현재 스테이지 지형+플레이어)과 M 키 전체 방 목록. 픽셀 계산은 이 클래스(ui)에서만 한다. */
public final class MiniMap {

    private static final int MAX_W = 320;
    private static final int MAX_H = 160;
    private static final int MAX_TILE_PX = 8;
    private static final int PAD = 10;
    private static final int MINI_PAD = 0; // 미니맵은 테두리와 패널 사이 여백 없이 꽉 채운다
    private static final int DOT_SIZE = 10;
    private static final Color PANEL_BG = new Color(0, 0, 0, 90); // 투명 배경
    private static final Color WALL = new Color(255, 255, 255, 170);
    private static final Color PLAYER = new Color(255, 70, 70);
    private static final Color EXIT = new Color(80, 230, 120);
    private static final int EXIT_MIN_PX = 6;
    private static final Color CURRENT = new Color(255, 210, 60);
    private static final Color CLEARED = new Color(120, 220, 120);
    private static final Color LOCKED = new Color(160, 160, 160);
    private static final Font TITLE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 22);
    private static final Font ROOM_FONT = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final Font NUMBER_FONT = new Font(Font.MONOSPACED, Font.BOLD, 40);
    private static final Color POPUP_BG = new Color(10, 12, 24, 215);
    private static final Color ARROW = new Color(220, 220, 220);
    private static final int ROOM_SIZE = 110;
    private static final int ROOM_GAP = 70;
    private static final int LABEL_H = 26;
    private static final int POPUP_MAX_W = 900;
    private static final int POPUP_MARGIN = 30;
    private static final int GLOW_LAYERS = 6;

    private final Supplier<List<Stage>> stagesSupplier;
    private boolean overviewVisible;

    private Stage cachedStage;
    private int cachedTilePx;
    private BufferedImage cachedTerrain;

    public MiniMap(Supplier<List<Stage>> stagesSupplier) {
        this.stagesSupplier = stagesSupplier;
    }

    public void toggleOverview() {
        overviewVisible = !overviewVisible;
    }

    public boolean isOverviewVisible() {
        return overviewVisible;
    }

    public void closeOverview() {
        overviewVisible = false;
    }

    /** 미니맵은 (x, y) 왼쪽 위에, 전체 진행 팝업은 뷰포트(vx, vy, vw, vh) 가운데에 그린다. */
    public void draw(Graphics2D g2, GameState state, int x, int y, int vx, int vy, int vw, int vh) {
        drawMiniMap(g2, state, x, y);
        if (overviewVisible) {
            drawOverview(g2, state.getStage(), vx, vy, vw, vh);
        }
    }

    private void drawMiniMap(Graphics2D g2, GameState state, int x, int y) {
        Stage stage = state.getStage();
        int tilePx = Math.max(1, Math.min(MAX_TILE_PX,
                Math.min(MAX_W / stage.getWidth(), MAX_H / stage.getHeight())));
        int w = stage.getWidth() * tilePx;
        int h = stage.getHeight() * tilePx;

        g2.setColor(PANEL_BG);
        g2.fillRect(x, y, w + MINI_PAD * 2, h + MINI_PAD * 2);
        g2.drawImage(terrainImage(stage, tilePx), x + MINI_PAD, y + MINI_PAD, null);

        // 다음 스테이지로 넘어가는 문(EXIT)을 초록 표시로 그린다.
        for (GameObject obj : stage.getObjects()) {
            if (obj.getType() == ObjectType.EXIT) {
                int ex = x + MINI_PAD + obj.getX() * tilePx;
                int ey = y + MINI_PAD + obj.getY() * tilePx;
                int ew = Math.max(EXIT_MIN_PX, obj.getWidth() * tilePx);
                int eh = Math.max(EXIT_MIN_PX, obj.getHeight() * tilePx);
                g2.setColor(EXIT);
                g2.fillRect(ex, ey, ew, eh);
                g2.setColor(Color.WHITE);
                g2.drawRect(ex, ey, ew, eh);
            }
        }

        // 플레이어 위치(타일 → 미니맵 픽셀)
        double px = state.getPlayer().getX() + GameConstants.PLAYER_WIDTH / 2;
        double py = state.getPlayer().getY() + GameConstants.PLAYER_HEIGHT / 2;
        int dx = x + MINI_PAD + (int) (px * tilePx) - DOT_SIZE / 2;
        int dy = y + MINI_PAD + (int) (py * tilePx) - DOT_SIZE / 2;
        g2.setColor(PLAYER);
        g2.fillOval(dx, dy, DOT_SIZE, DOT_SIZE);
    }

    /** 지형 이미지는 스테이지/배율이 바뀔 때만 다시 만든다. */
    private BufferedImage terrainImage(Stage stage, int tilePx) {
        if (stage == cachedStage && tilePx == cachedTilePx && cachedTerrain != null) {
            return cachedTerrain;
        }
        BufferedImage img = new BufferedImage(stage.getWidth() * tilePx, stage.getHeight() * tilePx,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(WALL);
        for (int ty = 0; ty < stage.getHeight(); ty++) {
            for (int tx = 0; tx < stage.getWidth(); tx++) {
                if (stage.isSolidTile(tx, ty)) {
                    // 맵 가장자리 타일은 두께를 절반으로 그려 테두리를 얇게 보이게 한다(바깥 쪽에 붙여서).
                    // 모서리에서도 선이 끊기지 않도록 가로 띠와 세로 띠를 각각 그린다.
                    int half = Math.max(1, tilePx / 2);
                    int rx = tx * tilePx;
                    int ry = ty * tilePx;
                    boolean sideX = tx == 0 || tx == stage.getWidth() - 1;
                    boolean sideY = ty == 0 || ty == stage.getHeight() - 1;
                    if (!sideX && !sideY) {
                        g.fillRect(rx, ry, tilePx, tilePx);
                    }
                    if (sideY) {
                        g.fillRect(rx, (ty == 0) ? ry : ry + tilePx - half, tilePx, half);
                    }
                    if (sideX) {
                        g.fillRect((tx == 0) ? rx : rx + tilePx - half, ry, half, tilePx);
                    }
                }
            }
        }
        g.dispose();
        cachedStage = stage;
        cachedTilePx = tilePx;
        cachedTerrain = img;
        return img;
    }

    /** 가운데 팝업: 방을 네모로, 순서대로 화살표로 잇는다. 줄이 넘치면 뱀 모양으로 이어 화살표가 항상 이웃한 방을 잇게 한다. */
    private void drawOverview(Graphics2D g2, Stage current, int vx, int vy, int vw, int vh) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<Stage> stages = stagesSupplier.get();
        int total = stages.size();

        int popupW = Math.min(POPUP_MAX_W, vw - POPUP_MARGIN * 2);
        int perRow = Math.max(1, (popupW - PAD * 4 + ROOM_GAP) / (ROOM_SIZE + ROOM_GAP));
        int rows = (total + perRow - 1) / perRow;
        int titleH = 50;
        int popupH = titleH + rows * (ROOM_SIZE + LABEL_H) + (rows - 1) * ROOM_GAP + PAD * 4;
        int px = vx + (vw - popupW) / 2;
        int py = vy + (vh - popupH) / 2;

        g2.setColor(POPUP_BG);
        g2.fillRoundRect(px, py, popupW, popupH, 18, 18);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(px, py, popupW, popupH, 18, 18);
        g2.setFont(TITLE_FONT);
        FontMetrics tfm = g2.getFontMetrics();
        String title = "STAGES  [M]";
        g2.drawString(title, px + (popupW - tfm.stringWidth(title)) / 2, py + PAD * 2 + tfm.getAscent() - 4);

        int usedCols = Math.min(perRow, total);
        int gridW = usedCols * ROOM_SIZE + (usedCols - 1) * ROOM_GAP;
        int gridX = px + (popupW - gridW) / 2;
        int gridY = py + titleH + PAD;

        for (int i = 0; i < total; i++) {
            int[] pos = roomOrigin(i, perRow, gridX, gridY);
            if (i + 1 < total) {
                drawArrow(g2, pos, roomOrigin(i + 1, perRow, gridX, gridY));
            }
        }
        for (int i = 0; i < total; i++) {
            int[] pos = roomOrigin(i, perRow, gridX, gridY);
            drawRoom(g2, stages.get(i), current, pos[0], pos[1]);
        }
    }

    private static int[] roomOrigin(int index, int perRow, int gridX, int gridY) {
        int row = index / perRow;
        int col = index % perRow;
        if (row % 2 == 1) {
            col = perRow - 1 - col; // 홀수 줄은 오른쪽에서 왼쪽으로
        }
        return new int[] {gridX + col * (ROOM_SIZE + ROOM_GAP),
                gridY + row * (ROOM_SIZE + LABEL_H + ROOM_GAP)};
    }

    private void drawRoom(Graphics2D g2, Stage stage, Stage current, int x, int y) {
        boolean isCurrent = stage.getNumber() == current.getNumber();
        boolean cleared = stage.getNumber() < current.getNumber();
        Color c = isCurrent ? CURRENT : (cleared ? CLEARED : LOCKED);

        if (isCurrent) {
            drawGlow(g2, x, y);
        }
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), isCurrent ? 80 : 35));
        g2.fillRoundRect(x, y, ROOM_SIZE, ROOM_SIZE, 10, 10);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(isCurrent ? 3f : 2f));
        g2.drawRoundRect(x, y, ROOM_SIZE, ROOM_SIZE, 10, 10);
        g2.setStroke(new BasicStroke(1f));

        int cx = x + ROOM_SIZE / 2;
        int cy = y + ROOM_SIZE / 2;
        if (stage.getType() == StageType.BOSS) {
            drawBossIcon(g2, cx, cy, isCurrent ? CURRENT : Color.WHITE);
        } else {
            g2.setFont(NUMBER_FONT);
            FontMetrics fm = g2.getFontMetrics();
            String num = String.valueOf(stage.getNumber());
            g2.setColor(c);
            g2.drawString(num, cx - fm.stringWidth(num) / 2, cy + fm.getAscent() / 2 - 4);
        }

        g2.setFont(ROOM_FONT);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(isCurrent ? CURRENT : Color.WHITE);
        String label = stage.getTitle();
        g2.drawString(label, cx - fm.stringWidth(label) / 2, y + ROOM_SIZE + 18);
    }

    /** 현재 방에 빛이 들어오는 느낌: 바깥으로 갈수록 옅어지는 겹 테두리, 시간에 따라 살짝 맥동. */
    private void drawGlow(Graphics2D g2, int x, int y) {
        double pulse = 0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 300.0);
        for (int i = GLOW_LAYERS; i >= 1; i--) {
            int grow = i * 5;
            int alpha = (int) (60 * pulse * (GLOW_LAYERS - i + 1) / GLOW_LAYERS);
            g2.setColor(new Color(CURRENT.getRed(), CURRENT.getGreen(), CURRENT.getBlue(), alpha));
            g2.fillRoundRect(x - grow, y - grow, ROOM_SIZE + grow * 2, ROOM_SIZE + grow * 2, 20 + grow, 20 + grow);
        }
    }

    private static void drawArrow(Graphics2D g2, int[] from, int[] to) {
        int half = ROOM_SIZE / 2;
        int ax = from[0] + half;
        int ay = from[1] + half;
        int bx = to[0] + half;
        int by = to[1] + half;
        double len = Math.hypot(bx - ax, by - ay);
        double ux = (bx - ax) / len;
        double uy = (by - ay) / len;
        int margin = half + 8; // 방 가장자리 바깥에서 시작/끝
        int sx = (int) (ax + ux * margin);
        int sy = (int) (ay + uy * margin);
        int ex = (int) (bx - ux * margin);
        int ey = (int) (by - uy * margin);

        g2.setColor(ARROW);
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(sx, sy, ex, ey);
        g2.setStroke(new BasicStroke(1f));
        int head = 10;
        Polygon tip = new Polygon();
        tip.addPoint(ex + (int) (ux * head), ey + (int) (uy * head));
        tip.addPoint(ex - (int) (uy * head * 0.7), ey + (int) (ux * head * 0.7));
        tip.addPoint(ex + (int) (uy * head * 0.7), ey - (int) (ux * head * 0.7));
        g2.fillPolygon(tip);
    }

    /** 보스 아이콘: 해골. */
    private static void drawBossIcon(Graphics2D g2, int cx, int cy, Color color) {
        g2.setColor(color);
        g2.fillOval(cx - 26, cy - 30, 52, 46);
        g2.fillRoundRect(cx - 15, cy + 8, 30, 24, 8, 8);
        g2.setColor(new Color(10, 12, 24));
        g2.fillOval(cx - 17, cy - 12, 14, 16);
        g2.fillOval(cx + 3, cy - 12, 14, 16);
        g2.fillPolygon(new int[] {cx, cx - 4, cx + 4}, new int[] {cy + 4, cy + 14, cy + 14}, 3);
        for (int t = -8; t <= 8; t += 8) {
            g2.fillRect(cx + t - 1, cy + 20, 2, 12);
        }
    }
}
