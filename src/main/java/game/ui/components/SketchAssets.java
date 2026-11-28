package game.ui.components;

import game.ui.ImageLoader;

import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 손그림 퀴즈 화면(escape_quiz_assets)의 이미지 모음.
 *
 * 파일 읽기와 기억은 {@link ImageLoader}가 맡고, 이 클래스는 표시할 때만 쓰는 가공을 한다.
 * <ul>
 *   <li>{@link #trimmed}: 투명 여백을 잘라 낸 보기(view). 원본 이미지와 파일은 수정하지 않는다.</li>
 *   <li>{@link #scaled}: 화면 크기에 맞춰 줄인 그림(같은 크기는 기억). 큰 원본(1254px 등)을 한 번에 줄이면 계단이
 *   생기므로 절반씩 여러 번 줄인다.</li>
 * </ul>
 */
public final class SketchAssets {

    /** 이미지 폴더 (resources 기준) */
    public static final String ROOT = "escape_quiz_assets/";

    public static final String LOGO = ROOT + "logo.png";
    public static final String STAGE_FRAME = ROOT + "stage_frame.png";
    public static final String BUTTON_EXIT = ROOT + "button_exit.png";
    public static final String BUTTON_WIDE = ROOT + "button_wide.png";
    public static final String BUTTON_SQUARE = ROOT + "button_square.png";
    public static final String BUTTON_SUBMIT = ROOT + "button_submit.png";
    public static final String INPUT_FRAME = ROOT + "input_frame.png";
    public static final String QUIZ_FRAME = ROOT + "quiz_frame.png";
    public static final String TIMER_LINE = ROOT + "timer_line.png";
    public static final String FIRE = ROOT + "fire.png";
    public static final String HP_FULL = ROOT + "hp_full.png";
    public static final String HP_EMPTY = ROOT + "hp_empty.png";

    /** 투명하다고 보는 알파 값의 한계 (이보다 크면 그림으로 본다) */
    private static final int ALPHA_LIMIT = 24;
    private static final int MAX_CACHED_SCALES = 64;

    private final ImageLoader loader;
    private final Map<String, Optional<BufferedImage>> trimmed = new HashMap<>();
    private final Map<String, BufferedImage> scaledCache = new HashMap<>();

    public SketchAssets(ImageLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader is null");
        }
        this.loader = loader;
    }

    public ImageLoader loader() {
        return loader;
    }

    /** 투명 여백을 잘라 낸 이미지(원본의 일부를 보는 view). 파일이 없으면 빈 값. */
    public synchronized Optional<BufferedImage> trimmed(String file) {
        Optional<BufferedImage> cached = trimmed.get(file);
        if (cached != null) {
            return cached;
        }
        Optional<BufferedImage> result = loader.get(file).map(img -> {
            Rectangle r = contentBounds(img);
            return r.width <= 0 || r.height <= 0 ? img : img.getSubimage(r.x, r.y, r.width, r.height);
        });
        trimmed.put(file, result);
        return result;
    }

    /** 여백을 자른 그림의 가로/세로 비율(너비÷높이). 그림이 없으면 1. */
    public double aspect(String file) {
        return trimmed(file).map(i -> (double) i.getWidth() / i.getHeight()).orElse(1.0);
    }

    /** 여백을 자른 그림을 w×h로 줄인(또는 늘린) 그림. 같은 크기는 기억해 둔다. 그림이 없으면 빈 값. */
    public synchronized Optional<BufferedImage> scaled(String file, int w, int h) {
        if (w <= 0 || h <= 0) {
            return Optional.empty();
        }
        String key = file + '|' + w + 'x' + h;
        BufferedImage cached = scaledCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<BufferedImage> src = trimmed(file);
        if (src.isEmpty()) {
            return Optional.empty();
        }
        BufferedImage out = scale(src.get(), w, h);
        if (scaledCache.size() >= MAX_CACHED_SCALES) {
            scaledCache.clear();
        }
        scaledCache.put(key, out);
        return Optional.of(out);
    }

    /** 절반씩 줄여 가며 목표 크기로 만든다 (한 번에 크게 줄일 때 생기는 계단·잡티 방지). */
    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = draw(cur, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    /** 거의 투명하지 않은 픽셀의 경계 사각형. 전부 투명하면 이미지 전체. */
    public static Rectangle contentBounds(BufferedImage img) {
        int minX = img.getWidth();
        int minY = img.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) > ALPHA_LIMIT) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? new Rectangle(0, 0, img.getWidth(), img.getHeight())
                : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
