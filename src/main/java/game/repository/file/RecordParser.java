package game.repository.file;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 한 줄짜리 레코드(Quiz, User, Save)를 읽는 공용 도우미. 구분자와 쓰기용 형식도 함께 둔다.
 *
 * <p>형식이 틀리면 IllegalArgumentException을 던진다. 파일 이름과 줄 번호는 여기서 모르고, 줄을 읽는 저장소가
 * DataFormatException으로 바꾸면서 붙인다.
 */
final class RecordParser {

    /** 읽을 때 쓰는 필드 구분자(정규식). split("|")은 정규식 때문에 잘못 잘린다. */
    static final String FIELD_SPLIT = "\\|";

    /** 쓸 때 쓰는 필드 구분자. */
    static final String FIELD_JOIN = "|";

    /** 필드 안의 ID·항목 목록 구분자. */
    static final String LIST_DELIMITER = ",";

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RecordParser() {
    }

    /** 한 줄을 칸으로 나누고 칸 수를 검사한다. -1은 끝의 빈 칸이 사라지지 않게 한다. */
    static String[] splitFields(String line, int expected) {
        String[] fields = line.split(FIELD_SPLIT, -1);
        if (fields.length != expected) {
            throw new IllegalArgumentException("칸이 " + expected + "개여야 하는데 " + fields.length + "개입니다");
        }
        return fields;
    }

    static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "이(가) 숫자가 아닙니다: '" + value + "'");
        }
    }

    static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("일시 형식이 yyyy-MM-dd HH:mm이 아닙니다: '" + value + "'");
        }
    }
}
