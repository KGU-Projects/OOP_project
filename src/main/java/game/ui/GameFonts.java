package game.ui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 게임 화면의 글꼴 역할별 제공처. 글꼴 파일은 resources/escape_quiz_assets/fonts/ 에 있고(모두 SIL OFL 1.1, 같은 폴더 README.md 참고),
 * 파일이 없거나 읽지 못하면 시스템 글꼴로 대체하므로 글꼴 없이도 화면이 정상 동작한다.
 *
 * <ul>
 *   <li>{@link #text(int, int)}: 문제·보기·해설 (Pretendard). 굵게(Font.BOLD)는 별도 굵은 파일을 쓴다.</li>
 *   <li>{@link #hand(float)}: 닉네임·몬스터 이름·HP·STAGE·TIME·ESC·PAUSED 같은 짧은 표시 문구 전용 손글씨 글꼴(Gaegu Bold).</li>
 *   <li>{@link #label(float)}: 버튼·입력창 안내처럼 읽기 쉬워야 하는 짧은 UI 문구(Pretendard Regular).
 *   {@link #title(float)}은 팝업 제목·정답/오답 문구용 Pretendard Bold다.</li>
 *   <li>{@link #code(float)}: Java 코드. 도트 글꼴이 아닌 고정폭 글꼴이며, 프로젝트에 고정폭 파일이 없어 설치된 글꼴 중
 *   Consolas → Cascadia Mono → Menlo → DejaVu Sans Mono 순으로 쓰고 모두 없으면 Monospaced다.</li>
 * </ul>
 */
public final class GameFonts {

    private static final String TEXT_REGULAR_FILE = "escape_quiz_assets/fonts/Pretendard-Regular.otf";
    private static final String TEXT_BOLD_FILE = "escape_quiz_assets/fonts/Pretendard-Bold.otf";
    private static final String HAND_FILE = "escape_quiz_assets/fonts/Gaegu-Bold.ttf";

    /** 파일 글꼴을 못 읽었을 때 쓰는 시스템 글꼴 후보 (앞에서부터 설치된 것을 쓴다). */
    private static final String[] SYSTEM_TEXT = {"Pretendard", "Malgun Gothic", "맑은 고딕", "Noto Sans KR",
            "NanumGothic", "Apple SD Gothic Neo"};

    /** 코드용 고정폭 시스템 글꼴 후보 (앞에서부터 설치된 것을 쓴다). */
    private static final String[] SYSTEM_CODE = {"Consolas", "Cascadia Mono", "Menlo", "DejaVu Sans Mono"};

    private static final Map<String, Font> LOADED = new HashMap<>();
    private static String systemTextFamily;
    private static String systemCodeFamily;

    private GameFonts() {
    }

    /** 읽기용 글꼴(문제·보기·해설). style은 Font.PLAIN 또는 Font.BOLD. */
    public static Font text(int style, int size) {
        Font base = load((style & Font.BOLD) != 0 ? TEXT_BOLD_FILE : TEXT_REGULAR_FILE);
        return base != null ? base.deriveFont(Font.PLAIN, (float) size) : new Font(systemText(), style, size);
    }

    /** 읽기용 글꼴을 소수 크기로 (화면 배율에 맞출 때). */
    public static Font text(int style, float size) {
        Font base = load((style & Font.BOLD) != 0 ? TEXT_BOLD_FILE : TEXT_REGULAR_FILE);
        return base != null ? base.deriveFont(Font.PLAIN, size) : new Font(systemText(), style, Math.round(size));
    }

    /** 버튼·안내 같은 짧은 읽기용 UI 문구 글꼴: Pretendard Regular. (표시용 라벨은 {@link #hand(float)}) */
    public static Font label(float size) {
        return text(Font.PLAIN, size);
    }

    /**
     * 화면 장식용 손글씨 글꼴(Gaegu Bold, OFL): 닉네임·몬스터 이름·HP·STAGE·TIME·ESC·PAUSED처럼 짧은 표시 문구 전용이다.
     * 문제·보기·입력창·버튼·팝업 문구·해설에는 쓰지 않는다(그쪽은 {@link #text}). 파일을 못 읽으면 Pretendard로 대신한다.
     */
    public static Font hand(float size) {
        Font base = load(HAND_FILE);
        return base != null ? base.deriveFont(Font.PLAIN, size) : text(Font.BOLD, size);
    }

    /** 강조 문구(팝업 제목, 정답·오답 판정)용 글꼴: Pretendard Bold */
    public static Font title(float size) {
        return text(Font.BOLD, size);
    }

    /** 코드 글꼴을 소수 크기로. 고정폭이다. */
    public static Font code(float size) {
        return new Font(systemCode(), Font.PLAIN, 1).deriveFont(size);
    }

    /** 코드 글꼴(고정폭). */
    public static Font code(int size) {
        return code((float) size);
    }

    /** 코드 글꼴에 없는 글자(한글 주석 등)가 있는 줄에 쓸 대체 고정폭 글꼴 (논리 글꼴이라 한글은 시스템 글꼴로 이어진다). */
    public static Font codeFallback(float size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size);
    }

    /** 글꼴 파일을 실제로 읽었는지 (테스트·진단용). */
    public static boolean isLoaded(String resourcePath) {
        return load(resourcePath) != null;
    }

    private static synchronized Font load(String path) {
        if (LOADED.containsKey(path)) {
            return LOADED.get(path);
        }
        Font font = null;
        try (InputStream in = GameFonts.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                System.err.println("글꼴 파일 없음(시스템 글꼴로 대체): " + path);
            } else {
                font = Font.createFont(Font.TRUETYPE_FONT, in);
            }
        } catch (FontFormatException | IOException e) {
            System.err.println("글꼴 읽기 실패(시스템 글꼴로 대체): " + path + " - " + e.getMessage());
        }
        LOADED.put(path, font);
        return font;
    }

    private static synchronized String systemCode() {
        if (systemCodeFamily == null) {
            Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            systemCodeFamily = Font.MONOSPACED;
            for (String name : SYSTEM_CODE) {
                if (available.contains(name)) {
                    systemCodeFamily = name;
                    break;
                }
            }
        }
        return systemCodeFamily;
    }

    /** 지금 쓰는 코드 글꼴 이름 (진단·보고용) */
    public static String codeFamilyName() {
        return systemCode();
    }

    private static String systemText() {
        if (systemTextFamily == null) {
            Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            systemTextFamily = Font.SANS_SERIF;
            for (String name : SYSTEM_TEXT) {
                if (available.contains(name)) {
                    systemTextFamily = name;
                    break;
                }
            }
        }
        return systemTextFamily;
    }
}
