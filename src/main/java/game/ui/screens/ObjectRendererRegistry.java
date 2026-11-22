package game.ui.screens;

import game.model.GameState;
import game.model.object.GameObject;
import game.model.object.ObjectType;
import game.ui.Camera;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.EnumMap;
import java.util.Map;

/**
 * ObjectType별 흑백 렌더러 전략.
 * 추후 이미지 리소스 적용 시 ImageLoader 기반 렌더러로 교체한다.
 */
final class ObjectRendererRegistry {

    @FunctionalInterface
    interface Renderer {
        void render(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby);
    }

    private final Map<ObjectType, Renderer> renderers = new EnumMap<>(ObjectType.class);

    static ObjectRendererRegistry createMonochromeDefault() {
        ObjectRendererRegistry registry = new ObjectRendererRegistry();
        registry.register(ObjectType.MONSTER, ObjectRendererRegistry::renderMonster);
        registry.register(ObjectType.CHEST, ObjectRendererRegistry::renderBox);
        registry.register(ObjectType.MIMIC, ObjectRendererRegistry::renderBox);
        registry.register(ObjectType.TRAP, ObjectRendererRegistry::renderTrap);
        registry.register(ObjectType.EXIT, ObjectRendererRegistry::renderExit);
        registry.register(ObjectType.MOVE_PLATFORM, ObjectRendererRegistry::renderMovePlatform);
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

    private static void renderMonster(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        g2.setColor(Color.WHITE);
        g2.fillRect(px + 2, py + 2, pw - 4, ph - 4);
        drawCenteredTag(g2, "M", px + pw / 2, py + ph / 2, Color.BLACK);
    }

    /** CHEST와 MIMIC 모두 화면에서는 동일한 상자(S)로 표시한다. */
    private static void renderBox(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

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

    private static void renderTrap(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        g2.setColor(Color.WHITE);
        g2.drawRect(px + 4, py + 4, pw - 9, ph - 9);
        drawCenteredTag(g2, "!", px + pw / 2, py + ph / 2, Color.WHITE);
    }

    private static void renderExit(Graphics2D g2, GameObject obj, GameState state, Camera camera, boolean nearby) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        g2.setColor(Color.WHITE);
        g2.drawRect(px + 2, py + 2, pw - 5, ph - 5);
        g2.drawRect(px + 5, py + 5, pw - 11, ph - 11);
        drawCenteredTag(g2, "D", px + pw / 2, py + ph / 2, Color.WHITE);
    }

    private static void renderMovePlatform(Graphics2D g2, GameObject obj, GameState state, Camera camera,
            boolean nearby) {
        int px = camera.worldToScreenX(obj.getEffectiveX(state));
        int py = camera.worldToScreenY(obj.getEffectiveY(state));
        int pw = camera.toPixels(obj.getWidth());
        int ph = camera.toPixels(obj.getHeight());

        g2.setColor(Color.WHITE);
        g2.fillRect(px, py, pw, ph);
        g2.setColor(Color.BLACK);
        g2.drawRect(px, py, pw - 1, ph - 1);
        drawCenteredTag(g2, "A", px + pw / 2, py + ph / 2, Color.BLACK);
    }

    static void drawCenteredTag(Graphics2D g2, String text, int cx, int cy, Color color) {
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(color);
        g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    static void drawPrompt(Graphics2D g2, String text, int cx, int bottomY) {
        if (text == null || text.isBlank()) {
            return;
        }
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
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