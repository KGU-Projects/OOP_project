package game.ui.screens;

import game.model.GameState;
import game.model.object.GameObject;
import game.model.object.ObjectType;
import game.ui.Camera;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * ObjectType별 렌더러 전략 (불투명 캐싱 텍스처 적용 및 흑백 폴백 지원).
 */
final class ObjectRendererRegistry {

    private static final Font TAG_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private static final Color TAG_BG = new Color(0, 0, 0, 180);

    @FunctionalInterface
    interface Renderer {
        void render(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby);
    }

    private final Map<ObjectType, Renderer> renderers = new EnumMap<>(ObjectType.class);

    static ObjectRendererRegistry createMonochromeDefault() {
        return createTexturedDefault(null);
    }

    static ObjectRendererRegistry createTexturedDefault(BufferedImage texture) {
        ObjectRendererRegistry registry = new ObjectRendererRegistry();
        Map<Long, BufferedImage> cache = new HashMap<>();

        // 신규 스테이지 스키마 오브젝트
        registry.register(ObjectType.MONSTER,
                (g2, obj, state, camera, nearby) -> renderMonster(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.CHEST,
                (g2, obj, state, camera, nearby) -> renderBox(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.MIMIC,
                (g2, obj, state, camera, nearby) -> renderBox(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.TRAP,
                (g2, obj, state, camera, nearby) -> renderTrap(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.EXIT,
                (g2, obj, state, camera, nearby) -> renderExit(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.MOVE_PLATFORM,
                (g2, obj, state, camera, nearby) -> renderMovePlatform(g2, obj, state, camera, nearby, texture, cache));

        // 기존 오브젝트
        registry.register(ObjectType.BUTTON,
                (g2, obj, state, camera, nearby) -> renderButton(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.DOOR,
                (g2, obj, state, camera, nearby) -> renderDoor(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.KEY,
                (g2, obj, state, camera, nearby) -> renderKey(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.ITEM,
                (g2, obj, state, camera, nearby) -> renderItem(g2, obj, state, camera, nearby, texture, cache));
        registry.register(ObjectType.BOSS,
                (g2, obj, state, camera, nearby) -> renderBoss(g2, obj, state, camera, nearby, texture, cache));
        return registry;
    }

    void register(ObjectType type, Renderer renderer) {
        renderers.put(type, renderer);
    }

    void render(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby) {
        if (!obj.isVisible(state)) {
            return;
        }
        Renderer renderer = renderers.get(obj.getType());
        if (renderer != null) {
            renderer.render(g2, obj, state, camera, nearby);
        }
    }

    private static BufferedImage getScaledOpaque(BufferedImage src, Map<Long, BufferedImage> cache,
            int w, int h, int alphaPercent) {
        if (src == null || w <= 0 || h <= 0) {
            return null;
        }
        long key = (((long) w & 0xFFFFL) << 32) | (((long) h & 0xFFFFL) << 16) | (alphaPercent & 0xFFFFL);
        BufferedImage cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage scaled;
        if (GraphicsEnvironment.isHeadless()) {
            scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        } else {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
            scaled = gc.createCompatibleImage(w, h, Transparency.OPAQUE);
        }

        Graphics2D g = scaled.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (alphaPercent < 100) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaPercent / 100f));
        }
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        cache.put(key, scaled);
        return scaled;
    }

    private static void drawTexture(Graphics2D g2, BufferedImage src, Map<Long, BufferedImage> cache,
            int x, int y, int w, int h, int alphaPercent) {
        BufferedImage scaled = getScaledOpaque(src, cache, w, h, alphaPercent);
        if (scaled != null) {
            g2.drawImage(scaled, x, y, null);
        }
    }

    private static void drawTexturedTag(Graphics2D g2, String text, int cx, int cy) {
        g2.setFont(TAG_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int padX = 4;
        int w = fm.stringWidth(text) + padX * 2;
        int h = fm.getHeight();
        int x = cx - w / 2;
        int y = cy - h / 2;

        g2.setColor(TAG_BG);
        g2.fillRect(x, y, w, h);
        g2.setColor(Color.WHITE);
        g2.drawString(text, x + padX, y + fm.getAscent());
    }

    private static void renderMonster(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 2, py + 2, pw - 4, ph - 4, 100);
            drawTexturedTag(g2, "M", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.fillRect(px + 2, py + 2, pw - 4, ph - 4);
        drawCenteredTag(g2, "M", px + pw / 2, py + ph / 2, Color.BLACK);
    }

    /** CHEST와 MIMIC 모두 화면에서는 동일한 상자(S)로 표시한다. */
    private static void renderBox(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            if (state.isObjectResolved(obj.getId())) {
                drawTexture(g2, texture, cache, px + 4, py + 4, pw - 8, ph - 8, 35);
                drawTexturedTag(g2, "OPEN", px + pw / 2, py + ph / 2);
            } else {
                drawTexture(g2, texture, cache, px + 4, py + 4, pw - 8, ph - 8, 100);
                drawTexturedTag(g2, "S", px + pw / 2, py + ph / 2);
            }
            if (nearby) {
                drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
            }
            return;
        }

        g2.setColor(Color.WHITE);
        if (state.isObjectResolved(obj.getId())) {
            g2.drawRect(px + 4, py + 4, pw - 9, ph - 9);
            drawCenteredTag(g2, "OPEN", px + pw / 2, py + ph / 2, Color.WHITE);
        } else {
            g2.fillRect(px + 4, py + 4, pw - 8, ph - 8);
            drawCenteredTag(g2, "S", px + pw / 2, py + ph / 2, Color.BLACK);
        }
        if (nearby) {
            drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
        }
    }

    private static void renderTrap(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 4, py + 4, pw - 8, ph - 8, 100);
            drawTexturedTag(g2, "!", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.drawRect(px + 4, py + 4, pw - 9, ph - 9);
        drawCenteredTag(g2, "!", px + pw / 2, py + ph / 2, Color.WHITE);
    }

    private static void renderExit(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 2, py + 2, pw - 4, ph - 4, 100);
            drawTexturedTag(g2, "D", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.drawRect(px + 2, py + 2, pw - 5, ph - 5);
        g2.drawRect(px + 5, py + 5, pw - 11, ph - 11);
        drawCenteredTag(g2, "D", px + pw / 2, py + ph / 2, Color.WHITE);
    }

    private static void renderMovePlatform(Graphics2D g2, GameObject obj, GameState state, Camera camera,
            boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px, py, pw, ph, 100);
            drawTexturedTag(g2, "A", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.fillRect(px, py, pw, ph);
        g2.setColor(Color.BLACK);
        g2.drawRect(px, py, pw - 1, ph - 1);
        drawCenteredTag(g2, "A", px + pw / 2, py + ph / 2, Color.BLACK);
    }

    private static void renderButton(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            if (!obj.canInteract(state)) {
                drawTexture(g2, texture, cache, px + 4, py + 4, pw - 8, ph - 8, 35);
                drawTexturedTag(g2, "OK", px + pw / 2, py + ph / 2);
            } else {
                drawTexture(g2, texture, cache, px + 4, py + 4, pw - 8, ph - 8, 100);
                drawTexturedTag(g2, "B", px + pw / 2, py + ph / 2);
            }
            if (nearby) {
                drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
            }
            return;
        }

        g2.setColor(Color.WHITE);
        if (!obj.canInteract(state)) {
            g2.drawRect(px + 4, py + 4, pw - 9, ph - 9);
            drawCenteredTag(g2, "OK", px + pw / 2, py + ph / 2, Color.WHITE);
        } else {
            g2.fillRect(px + 4, py + 4, pw - 8, ph - 8);
            drawCenteredTag(g2, "B", px + pw / 2, py + ph / 2, Color.BLACK);
        }
        if (nearby) {
            drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
        }
    }

    private static void renderDoor(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            if (!obj.isSolid(state)) {
                drawTexture(g2, texture, cache, px + 2, py + 2, pw - 4, ph - 4, 25);
            } else {
                drawTexture(g2, texture, cache, px + 2, py + 2, pw - 4, ph - 4, 100);
                g2.setColor(Color.BLACK);
                g2.drawLine(px + 2, py + 2, px + pw - 3, py + ph - 3);
                g2.drawLine(px + pw - 3, py + 2, px + 2, py + ph - 3);
            }
            return;
        }

        g2.setColor(Color.WHITE);
        if (!obj.isSolid(state)) {
            g2.drawRect(px + 2, py + 2, pw - 5, ph - 5);
        } else {
            g2.fillRect(px + 2, py + 2, pw - 4, ph - 4);
            g2.setColor(Color.BLACK);
            g2.drawLine(px + 2, py + 2, px + pw - 3, py + ph - 3);
            g2.drawLine(px + pw - 3, py + 2, px + 2, py + ph - 3);
        }
    }

    private static void renderKey(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 6, py + 6, pw - 12, ph - 12, 100);
            drawTexturedTag(g2, "K", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.fillRect(px + 6, py + 6, pw - 12, ph - 12);
        drawCenteredTag(g2, "K", px + pw / 2, py + ph / 2, Color.BLACK);
    }

    private static void renderItem(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 6, py + 6, pw - 12, ph - 12, 100);
            drawTexturedTag(g2, "I", px + pw / 2, py + ph / 2);
            return;
        }

        g2.setColor(Color.WHITE);
        g2.drawRect(px + 6, py + 6, pw - 13, ph - 13);
        drawCenteredTag(g2, "I", px + pw / 2, py + ph / 2, Color.WHITE);
    }

    private static void renderBoss(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby,
            BufferedImage texture, Map<Long, BufferedImage> cache) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        if (texture != null) {
            drawTexture(g2, texture, cache, px + 2, py + 2, pw - 4, ph - 4, 100);
            drawTexturedTag(g2, "BOSS", px + pw / 2, py + ph / 2);
            if (nearby) {
                drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
            }
            return;
        }

        g2.setColor(Color.WHITE);
        g2.fillRect(px + 2, py + 2, pw - 4, ph - 4);
        drawCenteredTag(g2, "BOSS", px + pw / 2, py + ph / 2, Color.BLACK);
        if (nearby) {
            drawPrompt(g2, obj.getInteractPrompt(), px + pw / 2, py - 6);
        }
    }

    static void drawCenteredTag(Graphics2D g2, String text, int cx, int cy, Color color) {
        g2.setFont(TAG_FONT);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(color);
        g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    static void drawPrompt(Graphics2D g2, String text, int cx, int bottomY) {
        if (text == null || text.isBlank()) {
            return;
        }
        g2.setFont(TAG_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + 10;
        int h = 18;
        int x = cx - w / 2;
        int y = bottomY - h;

        g2.setColor(Color.BLACK);
        g2.fillRect(x, y, w, h);
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, w, h);
        g2.drawString(text, x + 5, y + 13);
    }
}