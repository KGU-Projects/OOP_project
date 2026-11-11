package game.repository.file;

import game.model.ItemType;
import game.model.Stage;
import game.model.StageType;
import game.model.Tile;
import game.model.object.BossObject;
import game.model.object.ButtonObject;
import game.model.object.DoorObject;
import game.model.object.GameObject;
import game.model.object.ItemObject;
import game.model.object.KeyObject;
import game.model.object.ObjectType;
import game.repository.StageRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * data/stages/stageN.txt를 읽는 StageRepository 구현체. 형식은 docs/data-format.md를 따른다.
 * 형식이 틀리면 파일 이름과 줄 번호가 들어간 DataFormatException을 던진다.
 */
public class FileStageRepository implements StageRepository {

    private static final String FILE_PREFIX = "stage";
    private static final String FILE_SUFFIX = ".txt";
    private static final Pattern STAGE_FILE_PATTERN = Pattern.compile("stage(\\d{1,6})\\.txt");
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private static final String FIELD_DELIMITER = "\\|";
    private static final String LIST_DELIMITER = ",";

    private static final int HEADER_FIELD_COUNT = 7;       // 번호|제목|개념|유형|테마|가로|세로
    private static final int OBJECT_COMMON_FIELD_COUNT = 4; // ID|종류|x|y
    private static final int HEADER_LINE_NO = 1;
    private static final char START_CHAR = 'P';

    private final Path directory;

    public FileStageRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public List<Stage> findAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();   // 폴더가 아직 없으면 스테이지가 없는 것으로 본다
        }
        List<Path> files = listStageFiles();
        List<Stage> stages = new ArrayList<>();
        for (Path file : files) {
            stages.add(parse(file));
        }
        validateBossIsLast(stages);
        return stages;
    }

    @Override
    public Optional<Stage> findByNumber(int stageNo) {
        Path file = directory.resolve(FILE_PREFIX + stageNo + FILE_SUFFIX);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(parse(file));
    }

    // ---- 파일 목록 ----

    private List<Path> listStageFiles() {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(p -> STAGE_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(p -> fileNumberOf(p.getFileName().toString())))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("스테이지 폴더를 읽을 수 없습니다: " + directory, e);
        }
    }

    private static int fileNumberOf(String fileName) {
        Matcher matcher = STAGE_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("스테이지 파일 이름이 아닙니다: " + fileName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** 보스 스테이지는 하나이고 번호가 가장 커야 한다. 파일 하나만으로는 알 수 없어서 목록에서 검사한다. */
    private void validateBossIsLast(List<Stage> stages) {
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            if (stage.getType() == StageType.BOSS && i != stages.size() - 1) {
                throw new DataFormatException(FILE_PREFIX + stage.getNumber() + FILE_SUFFIX, HEADER_LINE_NO,
                        "보스 스테이지는 번호가 가장 큰 스테이지여야 합니다");
            }
        }
    }

    // ---- 파일 하나 읽기 ----

    private Stage parse(Path path) {
        String fileName = path.getFileName().toString();
        LineReader reader = new LineReader(fileName, TextFileIO.readLines(path));

        // 1. 정보 한 줄
        String[] header = reader.nextFields("정보 줄");
        reader.requireFieldCount(header, HEADER_FIELD_COUNT, "정보 줄");
        int number = reader.parseInt(header[0], "번호", 1);
        if (number != fileNumberOf(fileName)) {
            throw reader.error("정보 줄의 번호(" + number + ")가 파일 이름과 다릅니다");
        }
        String title = header[1];
        String concept = header[2];
        StageType type = parseStageType(reader, header[3]);
        int theme = reader.parseInt(header[4], "테마", 0);
        int width = reader.parseInt(header[5], "가로", 1);
        int height = reader.parseInt(header[6], "세로", 1);

        // 2. 지형: 세로 값만큼의 줄, 각 줄 길이는 가로 값. 'P'는 시작 위치로 기억하고 빈 공간으로 바꾼다.
        Tile[][] terrain = new Tile[height][width];
        int startX = -1;
        int startY = -1;
        for (int y = 0; y < height; y++) {
            String row = reader.nextLine("지형 " + (y + 1) + "번째 줄");
            if (row.length() != width) {
                throw reader.error("지형 줄의 길이가 " + row.length() + "입니다. 가로 값(" + width + ")과 같아야 합니다");
            }
            for (int x = 0; x < width; x++) {
                char c = row.charAt(x);
                if (c == START_CHAR) {
                    if (startX != -1) {
                        throw reader.error("시작 위치 P가 둘 이상입니다");
                    }
                    startX = x;
                    startY = y;
                    terrain[y][x] = Tile.EMPTY;
                } else if (c == Tile.SOLID.getSymbol() || c == Tile.EMPTY.getSymbol()) {
                    terrain[y][x] = Tile.fromChar(c);
                } else {
                    throw reader.error("알 수 없는 지형 문자 '" + c + "' (x=" + x + "). 지형에는 #, ., P만 씁니다");
                }
            }
        }
        if (startX == -1) {
            throw reader.errorAt(HEADER_LINE_NO, "지형에 시작 위치 P가 없습니다");
        }

        // 3. 오브젝트 개수와 오브젝트 줄
        int objectCount = reader.parseInt(reader.nextLine("오브젝트 개수").trim(), "오브젝트 개수", 0);
        List<GameObject> objects = new ArrayList<>();
        Map<String, Integer> lineNoById = new HashMap<>();
        for (int i = 0; i < objectCount; i++) {
            String[] fields = reader.nextFields("오브젝트 " + (i + 1) + "번째 줄");
            GameObject object = createObject(reader, fields);
            if (lineNoById.containsKey(object.getId())) {
                throw reader.error("오브젝트 ID '" + object.getId() + "'가 중복됩니다 (" + lineNoById.get(object.getId()) + "번째 줄)");
            }
            if (object.getX() + object.getWidth() > width || object.getY() + object.getHeight() > height) {
                throw reader.error("오브젝트 '" + object.getId() + "'가 맵 범위(" + width + " x " + height + ")를 벗어납니다");
            }
            lineNoById.put(object.getId(), reader.currentLineNo());
            objects.add(object);
        }
        reader.requireNoMoreContent();

        validateObjects(reader.getFileName(), type, objects, lineNoById);
        return new Stage(number, title, concept, type, theme, terrain, startX, startY, objects);
    }

    private StageType parseStageType(LineReader reader, String text) {
        try {
            return StageType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw reader.error("알 수 없는 스테이지 유형 '" + text + "' (NORMAL 또는 BOSS)");
        }
    }

    /** 오브젝트 사이의 관계 검사: 버튼이 가리키는 문, 열쇠와 보스의 개수. */
    private void validateObjects(String fileName, StageType type, List<GameObject> objects,
                                 Map<String, Integer> lineNoById) {
        Map<String, GameObject> byId = new HashMap<>();
        for (GameObject object : objects) {
            byId.put(object.getId(), object);
        }
        int keyCount = 0;
        int bossCount = 0;
        for (GameObject object : objects) {
            // 다른 오브젝트를 가리키는 오브젝트(버튼 → 문)는 대상이 있고 종류가 맞아야 한다
            for (String linkedId : object.getLinkedIds()) {
                GameObject target = byId.get(linkedId);
                boolean typeMatches = object.getLinkedType().map(t -> target != null && target.getType() == t).orElse(true);
                if (target == null || !typeMatches) {
                    throw new DataFormatException(fileName, lineNoById.get(object.getId()),
                            "'" + object.getId() + "'이(가) 가리키는 " + object.getLinkedType().map(Enum::name).orElse("오브젝트")
                                    + " '" + linkedId + "'이(가) 같은 파일에 없습니다");
                }
            }
            if (object.getType() == ObjectType.KEY) {
                keyCount++;
            } else if (object.getType() == ObjectType.BOSS) {
                bossCount++;
            }
        }
        if (type == StageType.NORMAL && (keyCount != 1 || bossCount != 0)) {
            throw new DataFormatException(fileName, HEADER_LINE_NO,
                    "일반 스테이지에는 열쇠가 정확히 하나, 보스는 없어야 합니다 (열쇠 " + keyCount + "개, 보스 " + bossCount + "개)");
        }
        if (type == StageType.BOSS && bossCount != 1) {
            throw new DataFormatException(fileName, HEADER_LINE_NO,
                    "보스 스테이지에는 보스가 정확히 하나 있어야 합니다 (보스 " + bossCount + "개)");
        }
    }

    // ---- 오브젝트 팩토리: 종류 문자열을 객체로 바꾸는 코드는 여기 한 곳에만 둔다 ----

    private GameObject createObject(LineReader reader, String[] fields) {
        if (fields.length < OBJECT_COMMON_FIELD_COUNT) {
            throw reader.error("오브젝트 줄은 최소 " + OBJECT_COMMON_FIELD_COUNT + "칸(ID|종류|x|y)이어야 합니다");
        }
        String id = fields[0];
        if (!OBJECT_ID_PATTERN.matcher(id).matches()) {
            throw reader.error("오브젝트 ID '" + id + "'는 영문, 숫자, _만 쓸 수 있습니다");
        }
        ObjectType type = parseObjectType(reader, fields[1]);
        int x = reader.parseInt(fields[2], "x", 0);
        int y = reader.parseInt(fields[3], "y", 0);

        switch (type) {
            case BUTTON: {
                reader.requireFieldCount(fields, OBJECT_COMMON_FIELD_COUNT + 2, "BUTTON 줄(문제ID, 열 문ID 목록)");
                int quizId = reader.parseInt(fields[4], "문제ID", 1);
                List<String> doorIds = Arrays.stream(fields[5].split(LIST_DELIMITER, -1)).map(String::trim).toList();
                if (doorIds.stream().anyMatch(String::isEmpty)) {
                    throw reader.error("BUTTON의 열 문ID 목록이 비어 있거나 ','가 잘못 쓰였습니다");
                }
                return new ButtonObject(id, x, y, quizId, doorIds);
            }
            case DOOR: {
                reader.requireFieldCount(fields, OBJECT_COMMON_FIELD_COUNT + 1, "DOOR 줄(높이)");
                return new DoorObject(id, x, y, reader.parseInt(fields[4], "문 높이", 1));
            }
            case KEY: {
                reader.requireFieldCount(fields, OBJECT_COMMON_FIELD_COUNT, "KEY 줄(세부값 없음)");
                return new KeyObject(id, x, y);
            }
            case ITEM: {
                reader.requireFieldCount(fields, OBJECT_COMMON_FIELD_COUNT + 2, "ITEM 줄(아이템 종류, 개수)");
                ItemType itemType = parseItemType(reader, fields[4]);
                return new ItemObject(id, x, y, itemType, reader.parseInt(fields[5], "아이템 개수", 1));
            }
            case BOSS: {
                reader.requireFieldCount(fields, OBJECT_COMMON_FIELD_COUNT + 2, "BOSS 줄(보스 이름, 최대 체력)");
                return new BossObject(id, x, y, fields[4], reader.parseInt(fields[5], "보스 최대 체력", 1));
            }
            default:
                throw reader.error("처리할 수 없는 오브젝트 종류: " + type);
        }
    }

    private ObjectType parseObjectType(LineReader reader, String text) {
        try {
            return ObjectType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw reader.error("알 수 없는 오브젝트 종류 '" + text + "'");
        }
    }

    private ItemType parseItemType(LineReader reader, String text) {
        try {
            return ItemType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw reader.error("알 수 없는 아이템 종류 '" + text + "'");
        }
    }

    // ---- 줄 번호를 기억하며 읽는 도우미 ----

    private static final class LineReader {

        private final String fileName;
        private final List<String> lines;
        private int nextIndex = 0;   // 다음에 읽을 줄(0부터)

        LineReader(String fileName, List<String> lines) {
            this.fileName = fileName;
            this.lines = lines;
        }

        String getFileName() {
            return fileName;
        }

        /** 방금 읽은 줄의 번호(1부터). */
        int currentLineNo() {
            return nextIndex;
        }

        String nextLine(String what) {
            if (nextIndex >= lines.size()) {
                throw errorAt(Math.max(lines.size(), 1), "줄이 부족합니다: " + what + "이(가) 필요합니다");
            }
            return lines.get(nextIndex++);
        }

        String[] nextFields(String what) {
            return nextLine(what).split(FIELD_DELIMITER, -1);   // -1: 끝의 빈 칸을 지우지 않는다
        }

        void requireFieldCount(String[] fields, int expected, String what) {
            if (fields.length != expected) {
                throw error(what + "은(는) " + expected + "칸이어야 하는데 " + fields.length + "칸입니다");
            }
        }

        int parseInt(String text, String name, int min) {
            int value;
            try {
                value = Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                throw error(name + " 값 '" + text + "'은(는) 정수가 아닙니다");
            }
            if (value < min) {
                throw error(name + " 값은 " + min + " 이상이어야 합니다 (현재 " + value + ")");
            }
            return value;
        }

        /** 오브젝트 줄 뒤에 내용이 남아 있으면 개수가 틀린 것이다. 끝의 빈 줄은 허용한다. */
        void requireNoMoreContent() {
            for (int i = nextIndex; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    throw errorAt(i + 1, "오브젝트 개수보다 줄이 더 많습니다");
                }
            }
        }

        /** 방금 읽은 줄에서 난 오류. */
        DataFormatException error(String message) {
            return errorAt(Math.max(nextIndex, 1), message);
        }

        DataFormatException errorAt(int lineNo, String message) {
            return new DataFormatException(fileName, lineNo, message);
        }
    }
}
