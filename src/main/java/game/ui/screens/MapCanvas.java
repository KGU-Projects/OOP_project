package game.ui.screens;

import game.common.GameConstants;
import game.model.GameState;
import game.model.ItemType;
import game.model.Player;
import game.model.Stage;
import game.model.object.GameObject;
import game.ui.Camera;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 40 x 20 타일 뷰포트를 순수 흑백(BLACK / WHITE)으로 렌더링하는 캔버스.
 */
final class MapCanvas extends JPanel {

    private final Camera camera;
    private final ObjectRendererRegistry rendererRegistry;
    private final Supplier<GameState> stateSupplier;
    private final Supplier<Optional<GameObject>> nearbySupplier;
    private final Supplier<String> statusSupplier;

    MapCanvas(Camera camera,
            ObjectRendererRegistry rendererRegistry,
            Supplier<GameState> stateSupplier,
            Supplier<Optional<GameObject>> nearbySupplier,
            Supplier<String> statusSupplier) {
        this.camera = camera;
        this.rendererRegistry = rendererRegistry;
        this.stateSupplier = stateSupplier;
        this.nearbySupplier = nearbySupplier;
        this.statusSupplier = statusSupplier;

        setFocusable(true);
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GameState state = stateSupplier.get();
        if (state == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        camera.updateViewportMetrics(getWidth(), getHeight());

        int vx = camera.getOriginX();
        int vy = camera.getOriginY();
        int vw = camera.getViewportPixelW();
        int vh = camera.getViewportPixelH();

        g2.setClip(vx, vy, vw, vh);
        drawVisibleTerrain(g2, state.getStage());
        drawObjects(g2, state);
        drawPlayer(g2, state.getPlayer());
        g2.setClip(null);

        g2.setColor(Color.WHITE);
        g2.drawRect(vx, vy, vw - 1, vh - 1);

        drawHud(g2, state, vx, vy, vw, vh);
        g2.dispose();
    }

    private void drawVisibleTerrain(Graphics2D g2, Stage stage) {
        int ts = camera.getTileSize();
        int startCol = camera.getFirstVisibleCol();
        int endCol = camera.getLastVisibleCol(stage.getWidth());
        int startRow = camera.getFirstVisibleRow();
        int endRow = camera.getLastVisibleRow(stage.getHeight());

        for (int y = startRow; y <= endRow; y++) {
            for (int x = startCol; x <= endCol; x++) {
                if (stage.isSolidTile(x, y)) {
                    int px = camera.worldToScreenX(x);
                    int py = camera.worldToScreenY(y);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(px, py, ts, ts);
                    g2.setColor(Color.BLACK);
                    g2.drawRect(px, py, ts - 1, ts - 1);
                }
            }
        }
    }

    private void drawObjects(Graphics2D g2, GameState state) {
        GameObject nearby = nearbySupplier.get().orElse(null);
        for (GameObject obj : state.getStage().getObjects()) {
            rendererRegistry.render(g2, obj, state, camera, obj == nearby);
        }
    }

    private void drawPlayer(Graphics2D g2, Player player) {
        int px = camera.worldToScreenX(player.getX());
        int py = camera.worldToScreenY(player.getY());
        int pw = camera.toPixels(GameConstants.PLAYER_WIDTH);
        int ph = camera.toPixels(GameConstants.PLAYER_HEIGHT);

        g2.setColor(Color.WHITE);
        g2.fillRect(px, py, pw, ph);
        g2.setColor(Color.BLACK);
        g2.drawRect(px, py, pw - 1, ph - 1);

        int eyeSize = Math.max(3, pw / 5);
        int eyeX = player.isFacingRight() ? px + pw - eyeSize - 3 : px + 3;
        int eyeY = py + Math.max(3, ph / 6);
        g2.fillRect(eyeX, eyeY, eyeSize, eyeSize);
    }

    private void drawHud(Graphics2D g2, GameState state, int vx, int vy, int vw, int vh) {
        Stage stage = state.getStage();
        String hud = String.format("STAGE %d (%s)  |  HP %d/%d  |  SCORE %d  |  POTION %d",
                stage.getNumber(),
                stage.getTitle(),
                state.getHp(),
                state.getMaxHp(),
                state.getScore(),
                state.getItemCount(ItemType.HEALTH_POTION));

        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        int boxW = fm.stringWidth(hud) + 16;
        int boxH = 24;

        g2.setColor(Color.BLACK);
        g2.fillRect(vx + 8, vy + 8, boxW, boxH);
        g2.setColor(Color.WHITE);
        g2.drawRect(vx + 8, vy + 8, boxW, boxH);
        g2.drawString(hud, vx + 16, vy + 24);

        String statusText = statusSupplier.get();
        if (statusText != null && !statusText.isBlank()) {
            ObjectRendererRegistry.drawPrompt(g2, statusText, vx + vw / 2, vy + 52);
        }

        if (state.isStageCleared()) {
            drawCenterNotice(g2, vx, vy, vw, vh, "STAGE CLEAR [ESC]");
        } else if (state.isDead()) {
            drawCenterNotice(g2, vx, vy, vw, vh, "GAME OVER [ESC]");
        }
    }

    private void drawCenterNotice(Graphics2D g2, int vx, int vy, int vw, int vh, String text) {
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 40;
        int h = 50;
        int x = vx + (vw - w) / 2;
        int y = vy + (vh - h) / 2;

        g2.setColor(Color.BLACK);
        g2.fillRect(x, y, w, h);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, w, h);
        g2.drawString(text, x + 20, y + 32);
    }
}