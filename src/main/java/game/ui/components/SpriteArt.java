package game.ui.components;

import game.ui.ImageLoader;
import game.ui.screens.QuizScreen;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * resources의 투명 PNG로 캐릭터를 그리는 {@link QuizScreen.CharacterArt}.
 *
 * 이미지는 {@link ImageLoader}가 읽고 기억한다. 파일이 없거나 읽지 못하면 fallback(임시 그림)이 대신 그려지므로
 * 이미지가 준비되기 전에도 화면이 비지 않는다. 그림은 투명 여백을 잘라 낸 뒤 비율을 유지한 채 주어진 영역 안에 맞추고,
 * 영역의 아래쪽 가운데에 놓는다. 원본 파일과 원본 이미지는 수정하지 않으며, 줄인 그림은 크기별로 기억해 두고 다시 쓴다.
 */
public final class SpriteArt implements QuizScreen.CharacterArt {

    private final ImageLoader loader;
    private final String fileName;
    private final QuizScreen.CharacterArt fallback;
    private BufferedImage cropSource;
    private Rectangle cropBounds;
    private BufferedImage scaledImage;
    private int scaledW;
    private int scaledH;

    /**
     * @param loader   이미지 로더
     * @param fileName resources 기준 파일 경로 (예: "escape_quiz_assets/player.png")
     * @param fallback 이미지가 없을 때 그릴 그림. null이면 아무것도 그리지 않는다
     */
    public SpriteArt(ImageLoader loader, String fileName, QuizScreen.CharacterArt fallback) {
        if (loader == null || fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("loader와 fileName이 필요합니다");
        }
        this.loader = loader;
        this.fileName = fileName;
        this.fallback = fallback;
    }

    @Override
    public void paint(Graphics2D g2, int x, int y, int w, int h) {
        Optional<BufferedImage> image = loader.get(fileName);
        if (image.isEmpty()) {
            if (fallback != null) {
                fallback.paint(g2, x, y, w, h);
            }
            return;
        }
        BufferedImage img = image.get();
        if (w <= 0 || h <= 0) {
            return;
        }
        Rectangle c = contentBounds(img);
        double scale = Math.min((double) w / c.width, (double) h / c.height);
        int dw = Math.max(1, (int) Math.round(c.width * scale));
        int dh = Math.max(1, (int) Math.round(c.height * scale));
        g2.drawImage(scaledView(img, c, dw, dh), x + (w - dw) / 2, y + h - dh, null);
    }

    /** 여백을 자른 그림을 dw×dh로 줄인 것. 같은 크기를 다시 그릴 때는 기억해 둔 것을 쓴다. */
    private BufferedImage scaledView(BufferedImage img, Rectangle c, int dw, int dh) {
        if (scaledImage == null || scaledW != dw || scaledH != dh || cropSource != img) {
            BufferedImage trimmed = img.getSubimage(c.x, c.y, c.width, c.height);
            scaledImage = SketchAssets.scale(trimmed, dw, dh);
            scaledW = dw;
            scaledH = dh;
        }
        return scaledImage;
    }

    /** 거의 투명하지 않은 픽셀의 경계. 이미지마다 한 번만 계산한다. */
    private Rectangle contentBounds(BufferedImage img) {
        if (img != cropSource || cropBounds == null) {
            cropBounds = SketchAssets.contentBounds(img);
            cropSource = img;
            scaledImage = null;
        }
        return cropBounds;
    }
}
