package game.ui.screens;

import game.common.GameConstants;
import game.model.GameState;
import game.model.ItemType;
import game.model.Player;
import game.model.Stage;
import game.model.object.GameObject;
import game.ui.Camera;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 40 x 20 타일 뷰포트를 렌더링하는 캔버스.
 */
final class MapCanvas extends JPanel {

    private static final Font HUD_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private static final Font NOTICE_FONT = new Font(Font.MONOSPACED, Font.BOLD, 20);
    private static final Font PLACEHOLDER_TAG_FONT = new Font(Font.MONOSPACED, Font.BOLD, 10);
    private static final Color PLACEHOLDER_TAG_BG = new Color(0, 0, 0, 190);
    private static final long MAX_TERRAIN_BUFFER_PIXELS = 16_777_216L;

    private final Camera camera;
    private final ObjectRendererRegistry rendererRegistry;
    private final Supplier<GameState> stateSupplier;
    private final Supplier<Optional<GameObject>> nearbySupplier;
    private final Supplier<String> statusSupplier;
    private final BufferedImage placeholderTexture;
    private final PlayerAnimator playerAnimator;
    private final Map<Long, BufferedImage> terrainSpriteCache = new HashMap<>();
    private final Map<Long, BufferedImage> playerSpriteCache = new HashMap<>();

    private Stage cachedTerrainStage;
    private int cachedTerrainTileSize = -1;
    private BufferedImage cachedTerrainLayer;

    private int cachedBgW = -1;
    private int cachedBgH = -1;
    private BufferedImage cachedBg;

    MapCanvas(Camera camera,
            ObjectRendererRegistry rendererRegistry,
            Supplier<GameState> stateSupplier,
            Supplier<Optional<GameObject>> nearbySupplier,
            Supplier<String> statusSupplier) {
        this(camera, rendererRegistry, stateSupplier, nearbySupplier, statusSupplier, null, null);
    }

    MapCanvas(Camera camera,
            ObjectRendererRegistry rendererRegistry,
            Supplier<GameState> stateSupplier,
            Supplier<Optional<GameObject>> nearbySupplier,
            Supplier<String> statusSupplier,
            BufferedImage placeholderTexture) {
        this(camera, rendererRegistry, stateSupplier, nearbySupplier, statusSupplier, placeholderTexture, null);
    }

    MapCanvas(Camera camera,
            ObjectRendererRegistry rendererRegistry,
            Supplier<GameState> stateSupplier,
            Supplier<Optional<GameObject>> nearbySupplier,
            Supplier<String> statusSupplier,
            BufferedImage placeholderTexture,
            PlayerAnimator playerAnimator) {
        this.camera = camera;
        this.rendererRegistry = rendererRegistry;
        this.stateSupplier = stateSupplier;
        this.nearbySupplier = nearbySupplier;
        this.statusSupplier = statusSupplier;
        this.placeholderTexture = placeholderTexture;
        this.playerAnimator = playerAnimator;

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
        int panelW = getWidth();
        int panelH = getHeight();
        camera.updateViewportMetrics(panelW, panelH);

        int vx = camera.getOriginX();
        int vy = camera.getOriginY();
        int vw = camera.getViewportPixelW();
        int vh = camera.getViewportPixelH();

        if (placeholderTexture != null) {
            BufferedImage bg = getOrBuildBackground(panelW, panelH);
            if (bg != null) {
                g2.drawImage(bg, 0, 0, null);
            }
        }

        g2.setClip(vx, vy, vw, vh);
        drawVisibleTerrain(g2, state.getStage());
        drawObjects(g2, state);
        drawPlayer(g2, state.getPlayer());
        g2.setClip(null);

        drawHud(g2, state, vx, vy, vw, vh);
        g2.dispose();
        Toolkit.getDefaultToolkit().sync();
    }

    private BufferedImage getOrBuildBackground(int w, int h) {
        if (placeholderTexture == null || w <= 0 || h <= 0) {
            return null;
        }
        if (w == cachedBgW && h == cachedBgH && cachedBg != null) {
            return cachedBg;
        }
        cachedBgW = w;
        cachedBgH = h;

        BufferedImage bg = createOpaqueImage(w, h);
        Graphics2D g = bg.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(placeholderTexture, 0, 0, w, h, null);
        g.dispose();

        cachedBg = bg;
        return cachedBg;
    }

    private void drawVisibleTerrain(Graphics2D g2, Stage stage) {
        int ts = camera.getTileSize();
        if (ts <= 0) {
            return;
        }

        BufferedImage terrainLayer = getOrBuildTerrainLayer(stage, ts);
        if (terrainLayer != null) {
            int stageScreenX = camera.worldToScreenX(0);
            int stageScreenY = camera.worldToScreenY(0);
            g2.drawImage(terrainLayer, stageScreenX, stageScreenY, null);
            return;
        }

        int startCol = camera.getFirstVisibleCol();
        int endCol = camera.getLastVisibleCol(stage.getWidth());
        int startRow = camera.getFirstVisibleRow();
        int endRow = camera.getLastVisibleRow(stage.getHeight());
        BufferedImage tileSprite = getScaledTerrainTile(ts, ts);

        for (int y = startRow; y <= endRow; y++) {
            for (int x = startCol; x <= endCol; x++) {
                if (stage.isSolidTile(x, y)) {
                    int px = camera.worldToScreenX(x);
                    int py = camera.worldToScreenY(y);
                    if (tileSprite != null) {
                        g2.drawImage(tileSprite, px, py, null);
                    } else {
                        g2.setColor(Color.WHITE);
                        g2.fillRect(px, py, ts, ts);
                        g2.setColor(Color.BLACK);
                        g2.drawRect(px, py, ts - 1, ts - 1);
                    }
                }
            }
        }
    }

    private BufferedImage getOrBuildTerrainLayer(Stage stage, int ts) {
        if (stage == cachedTerrainStage && ts == cachedTerrainTileSize) {
            return cachedTerrainLayer;
        }
        cachedTerrainStage = stage;
        cachedTerrainTileSize = ts;

        int w = stage.getWidth() * ts;
        int h = stage.getHeight() * ts;
        if (w <= 0 || h <= 0 || (long) w * h > MAX_TERRAIN_BUFFER_PIXELS) {
            cachedTerrainLayer = null;
            return null;
        }

        boolean hasBackground = placeholderTexture != null;
        BufferedImage layer = hasBackground ? createBitmaskImage(w, h) : createOpaqueImage(w, h);
        Graphics2D g = layer.createGraphics();
        if (hasBackground) {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
        }

        BufferedImage tileSprite = getScaledTerrainTile(ts, ts);
        for (int y = 0; y < stage.getHeight(); y++) {
            for (int x = 0; x < stage.getWidth(); x++) {
                if (stage.isSolidTile(x, y)) {
                    int px = x * ts;
                    int py = y * ts;
                    if (tileSprite != null) {
                        g.drawImage(tileSprite, px, py, null);
                    } else {
                        g.setColor(Color.WHITE);
                        g.fillRect(px, py, ts, ts);
                        g.setColor(Color.BLACK);
                        g.drawRect(px, py, ts - 1, ts - 1);
                    }
                }
            }
        }
        g.dispose();

        cachedTerrainLayer = layer;
        return layer;
    }

    private void drawObjects(Graphics2D g2, GameState state) {
        Stage stage = state.getStage();
        int startCol = camera.getFirstVisibleCol() - 1;
        int endCol = camera.getLastVisibleCol(stage.getWidth()) + 1;
        int startRow = camera.getFirstVisibleRow() - 1;
        int endRow = camera.getLastVisibleRow(stage.getHeight()) + 1;

        GameObject nearby = nearbySupplier.get().orElse(null);
        for (GameObject obj : stage.getObjects()) {
            double ox = obj.getEffectiveX(state);
            double oy = obj.getEffectiveY(state);
            if (ox + obj.getWidth() < startCol || ox > endCol
                    || oy + obj.getHeight() < startRow || oy > endRow) {
                continue;
            }
            rendererRegistry.render(g2, obj, state, camera, obj == nearby);
        }
    }

    private void drawPlayer(Graphics2D g2, Player player) {
        int px = camera.worldToScreenX(player.getX());
        int py = camera.worldToScreenY(player.getY());
        int pw = camera.toPixels(GameConstants.PLAYER_WIDTH);
        int ph = camera.toPixels(GameConstants.PLAYER_HEIGHT);

        BufferedImage playerSprite = getScaledPlayerSprite(pw, ph, !player.isFacingRight());
        if (playerSprite != null) {
            g2.drawImage(playerSprite, px, py, null);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.fillRect(px, py, pw, ph);
        g2.setColor(Color.BLACK);
        g2.drawRect(px, py, pw - 1, ph - 1);

        int eyeSize = Math.max(3, pw / 5);
        int eyeX = player.isFacingRight() ? px + pw - eyeSize - 3 : px + 3;
        int eyeY = py + Math.max(3, ph / 6);
        g2.fillRect(eyeX, eyeY, eyeSize, eyeSize);
    }

    private BufferedImage getScaledTerrainTile(int w, int h) {
        if (placeholderTexture == null || w <= 0 || h <= 0) {
            return null;
        }
        long key = (((long) w & 0xFFFFL) << 16) | (h & 0xFFFFL);
        BufferedImage cached = terrainSpriteCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage scaled = createOpaqueImage(w, h);
        Graphics2D g = scaled.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(placeholderTexture, 0, 0, w, h, null);
        g.dispose();

        terrainSpriteCache.put(key, scaled);
        return scaled;
    }

    private BufferedImage getScaledPlayerSprite(int w, int h, boolean flipX) {
        if (w <= 0 || h <= 0) {
            return null;
        }
        PlayerAnimator.Frame frame = (playerAnimator != null)
                ? playerAnimator.getCurrentFrame()
                : PlayerAnimator.Frame.IDLE;
        BufferedImage source = (playerAnimator != null)
                ? playerAnimator.getTexture(frame)
                : placeholderTexture;
        if (source == null) {
            return null;
        }

        long key = ((long) frame.ordinal() << 33)
                | ((flipX ? 1L : 0L) << 32)
                | (((long) w & 0xFFFFL) << 16)
                | (h & 0xFFFFL);
        BufferedImage cached = playerSpriteCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage scaled = createOpaqueImage(w, h);
        Graphics2D g = scaled.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (flipX) {
            g.drawImage(source, w, 0, -w, h, null);
        } else {
            g.drawImage(source, 0, 0, w, h, null);
        }

        if (playerAnimator != null && playerAnimator.isFallback(frame)) {
            drawPlaceholderBadge(g, frame.placeholderTag, w, h);
        }
        g.dispose();

        playerSpriteCache.put(key, scaled);
        return scaled;
    }

    private static void drawPlaceholderBadge(Graphics2D g, String tag, int w, int h) {
        g.setFont(PLACEHOLDER_TAG_FONT);
        FontMetrics fm = g.getFontMetrics();
        int padX = 2;
        int badgeW = Math.min(w, fm.stringWidth(tag) + padX * 2);
        int badgeH = fm.getHeight();
        int x = (w - badgeW) / 2;
        int y = Math.max(0, h - badgeH);

        g.setColor(PLACEHOLDER_TAG_BG);
        g.fillRect(x, y, badgeW, badgeH);
        g.setColor(Color.WHITE);
        int textX = (w - fm.stringWidth(tag)) / 2;
        g.drawString(tag, textX, y + fm.getAscent());
    }

    private static BufferedImage createOpaqueImage(int w, int h) {
        if (GraphicsEnvironment.isHeadless()) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        return gc.createCompatibleImage(w, h, Transparency.OPAQUE);
    }

    private static BufferedImage createBitmaskImage(int w, int h) {
        if (GraphicsEnvironment.isHeadless()) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        }
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        return gc.createCompatibleImage(w, h, Transparency.BITMASK);
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

        g2.setFont(HUD_FONT);
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
        g2.setFont(NOTICE_FONT);
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