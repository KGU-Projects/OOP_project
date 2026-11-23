package game.model.object;

import java.util.HashSet;
import java.util.Set;

/**
 * 오브젝트 종류.
 * - previewSymbol: 개발용 맵 미리보기·디버그 출력용 고유 문자 (모든 타입이 서로 다름)
 * - mapSymbol: 스테이지 파일 지형 격자에 적는 문자 (CHEST와 MIMIC은 위장을 위해 둘 다 'S'를 사용)
 */
public enum ObjectType {
    // 기존 오브젝트 (지형 격자에서는 '.' 칸 위에 배치)
    BUTTON('B', '.'),
    DOOR('d', '.'),
    KEY('K', '.'),
    ITEM('I', '.'),
    BOSS('W', '.'),

    // 신규 스테이지 스키마 오브젝트
    MONSTER('M', 'M'),
    CHEST('S', 'S'),
    MIMIC('?', 'S'),
    TRAP('T', '.'),
    EXIT('D', 'D'),
    MOVE_PLATFORM('A', 'A');

    private static final Set<Character> DEDICATED_MAP_SYMBOLS;

    static {
        Set<Character> uniquePreviews = new HashSet<>();
        Set<Character> mapSymbols = new HashSet<>();
        for (ObjectType type : values()) {
            if (!uniquePreviews.add(type.previewSymbol)) {
                throw new IllegalStateException("중복된 previewSymbol: " + type.previewSymbol);
            }
            if (type.mapSymbol != '.') {
                mapSymbols.add(type.mapSymbol);
            }
        }
        DEDICATED_MAP_SYMBOLS = Set.copyOf(mapSymbols);
    }

    private final char previewSymbol;
    private final char mapSymbol;

    ObjectType(char previewSymbol, char mapSymbol) {
        this.previewSymbol = previewSymbol;
        this.mapSymbol = mapSymbol;
    }

    /** 개발용 미리보기 고유 문자. */
    public char getSymbol() {
        return previewSymbol;
    }

    /** 스테이지 파일 지형 격자에 표시되는 문자. 전용 문자가 없으면 '.'. */
    public char getMapSymbol() {
        return mapSymbol;
    }

    public boolean hasDedicatedMapSymbol() {
        return mapSymbol != '.';
    }

    public static boolean isMapObjectSymbol(char c) {
        return DEDICATED_MAP_SYMBOLS.contains(c);
    }
}