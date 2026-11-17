package game.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

/** resources 폴더의 이미지를 불러오기(한 번 읽은 건 기억) */
public class ImageLoader {

    /** 이미지가 들어 있는 폴더(resources) */
    private static final String IMAGE_FOLDER = "/";

    /** 이미 읽은 이미지 저장(형식: 이미지 이름과 이미지 저장), 파일 없었으면 빈 상자로 기억 */
    private final Map<String, Optional<BufferedImage>> cache = new HashMap<>();

    /** 이미지 이름으로 이미지 가져오기. 파일 없으면 빈 상자 */
    public Optional<BufferedImage> get(String fileName) {
        // 파일 기억하고 있으면
        if(cache.containsKey(fileName)) {
            return cache.get(fileName);
        }
        Optional<BufferedImage> image = load(fileName);
        cache.put(fileName, image);
        return image;
    }

    /** resources에서 이미지 파일을 읽는다. 없거나 못 읽으면 빈 상자 */
    private Optional<BufferedImage> load(String fileName) {
        URL location = ImageLoader.class.getResource(IMAGE_FOLDER + fileName);
        if (location == null) {
            System.err.println("이미지 파일 없음: " + fileName);
            return Optional.empty();
        }

        try {
            BufferedImage image = ImageIO.read(location);
            return Optional.ofNullable(image);
        } catch (IOException e) {
            System.err.println("이미지 읽기 실패: " + fileName);
            return Optional.empty();
        }
    }
}
