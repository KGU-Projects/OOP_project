package game.repository.file;

import game.model.Stage;
import game.model.Tile;
import game.model.object.ChestObject;
import game.model.object.ExitObject;
import game.model.object.GameObject;
import game.model.object.MimicObject;
import game.model.object.MonsterObject;
import game.model.object.MovePlatformObject;
import game.model.object.ObjectType;
import game.model.object.RewardType;
import game.model.object.TrapObject;
import game.repository.StageRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * data/stages/stageN.txt를 읽는 StageRepository 구현체.
 * 형식:
 * - 헤더(5칸): 번호|제목|개념|가로|세로
 * - 지형(세로 줄): #(벽), .(빈 공간/TRAP), P(시작 위치), M(MONSTER), S(CHEST/MIMIC),
 * D(EXIT), A(MOVE_PLATFORM)
 * - 오브젝트(8칸): ID|TYPE|x|y|quizId|reward|trigger|extra
 */
public class FileStageRepository implements StageRepository {

    private static final String FILE_PREFIX = "stage";
    private static final String FILE_SUFFIX = ".txt";
    private static final Pattern STAGE_FILE_PATTERN = Pattern.compile("stage(\\d{1,6})\\.txt");
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private static final int HEADER_FIELD_COUNT = 5; // 번호|제목|개념|가로|세로
    private static final int OBJECT_FIELD_COUNT = 8; // ID|TYPE|x|y|quizId|reward|trigger|extra
    private static final int HEADER_LINE_NO = 1;
    private static final char START_CHAR = 'P';
    private static final String NO_EXTRA = "-";

    private final Path directory;

    public FileStageRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public List<Stage> findAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = listStageFiles();
        List<Stage> stages = new ArrayList<>();
        for (Path file : files) {
            stages.add(parse(file));
        }
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

    @Override
    public void print() {
        List<Stage> stages = findAll();
        System.out.println("=== 스테이지 (" + directory + ") " + stages.size() + "개 ===");
        stages.forEach(System.out::println);
    }

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

    private Stage parse(Path path) {
        String fileName = path.getFileName().toString();
        LineReader reader = new LineReader(fileName, TextFileIO.readLines(path));

        // 1. 헤더 한 줄: 번호|제목|개념|가로|세로
        String[] header = reader.nextFields("정보 줄");
        reader.requireFieldCount(header, HEADER_FIELD_COUNT, "정보 줄");
        int number = reader.parseInt(header[0], "번호", 1);
        if (number != fileNumberOf(fileName)) {
            throw reader.error("정보 줄의 번호(" + number + ")가 파일 이름과 다릅니다");
        }
        String title = header[1];
        String concept = header[2];
        int width = reader.parseInt(header[3], "가로", 1);
        int height = reader.parseInt(header[4], "세로", 1);

        // 2. 지형 격자 및 맵 오브젝트 마커 수집
        Tile[][] terrain = new Tile[height][width];
        Map<GridPos, Character> mapMarkers = new HashMap<>();
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
                } else if (ObjectType.isMapObjectSymbol(c)) {
                    terrain[y][x] = Tile.EMPTY;
                    mapMarkers.put(new GridPos(x, y), c);
                } else {
                    throw reader.error("알 수 없는 지형 문자 '" + c + "' (x=" + x + "). #, ., P, M, S, D, A만 씁니다");
                }
            }
        }
        if (startX == -1) {
            throw reader.errorAt(HEADER_LINE_NO, "지형에 시작 위치 P가 없습니다");
        }

        // 3. 오브젝트 개수 및 오브젝트 정의
        int objectCount = reader.parseInt(reader.nextLine("오브젝트 개수").trim(), "오브젝트 개수", 0);
        List<GameObject> objects = new ArrayList<>();
        Map<String, Integer> lineNoById = new HashMap<>();

        for (int i = 0; i < objectCount; i++) {
            String[] fields = reader.nextFields("오브젝트 " + (i + 1) + "번째 줄");
            GameObject object = createObject(reader, fields, width, height);
            if (lineNoById.containsKey(object.getId())) {
                throw reader
                        .error("오브젝트 ID '" + object.getId() + "'가 중복됩니다 (" + lineNoById.get(object.getId()) + "번째 줄)");
            }
            if (object.getX() + object.getWidth() > width || object.getY() + object.getHeight() > height) {
                throw reader.error("오브젝트 '" + object.getId() + "'가 맵 범위(" + width + " x " + height + ")를 벗어납니다");
            }
            lineNoById.put(object.getId(), reader.currentLineNo());
            objects.add(object);
        }
        reader.requireNoMoreContent();

        validateMapMarkers(reader.getFileName(), objects, lineNoById, mapMarkers);
        return new Stage(number, title, concept, terrain, startX, startY, objects);
    }

    private void validateMapMarkers(String fileName,
            List<GameObject> objects,
            Map<String, Integer> lineNoById,
            Map<GridPos, Character> mapMarkers) {
        Map<GridPos, Character> remaining = new HashMap<>(mapMarkers);

        for (GameObject obj : objects) {
            int lineNo = lineNoById.get(obj.getId());
            if (!obj.getType().hasDedicatedMapSymbol()) {
                GridPos pos = new GridPos(obj.getX(), obj.getY());
                if (remaining.containsKey(pos)) {
                    throw new DataFormatException(fileName, lineNo,
                            obj.getType() + " '" + obj.getId() + "' 위치(" + obj.getX() + "," + obj.getY()
                                    + ")는 '.' 칸이어야 합니다");
                }
                continue;
            }

            char expectedMapSymbol = obj.getType().getMapSymbol();
            for (int dy = 0; dy < obj.getHeight(); dy++) {
                for (int dx = 0; dx < obj.getWidth(); dx++) {
                    GridPos pos = new GridPos(obj.getX() + dx, obj.getY() + dy);
                    Character actual = remaining.remove(pos);
                    if (actual == null || actual != expectedMapSymbol) {
                        throw new DataFormatException(fileName, lineNo,
                                "'" + obj.getId() + "'(" + obj.getType() + ")의 (" + pos.x + "," + pos.y
                                        + ") 위치 지형 문자가 '" + expectedMapSymbol + "'가 아닙니다");
                    }
                }
            }
        }

        if (!remaining.isEmpty()) {
            GridPos orphan = remaining.keySet().iterator().next();
            throw new DataFormatException(fileName, HEADER_LINE_NO,
                    "지형 문자 '" + remaining.get(orphan) + "' (" + orphan.x + "," + orphan.y
                            + ")에 대응하는 오브젝트 정의가 없습니다");
        }
    }

    private GameObject createObject(LineReader reader, String[] fields, int mapWidth, int mapHeight) {
        reader.requireFieldCount(fields, OBJECT_FIELD_COUNT, "오브젝트 줄(ID|TYPE|x|y|quizId|reward|trigger|extra)");

        String id = fields[0].trim();
        if (!OBJECT_ID_PATTERN.matcher(id).matches()) {
            throw reader.error("오브젝트 ID '" + id + "'는 영문, 숫자, _만 쓸 수 있습니다");
        }

        ObjectType type = parseEnum(reader, ObjectType.class, fields[1].trim(), "오브젝트 종류");
        int x = reader.parseInt(fields[2], "x", 0);
        int y = reader.parseInt(fields[3], "y", 0);
        int quizId = reader.parseInt(fields[4], "quizId", 0);
        RewardType rewardType = parseEnum(reader, RewardType.class, fields[5].trim(), "보상 유형");
        game.model.object.TriggerType triggerType = parseEnum(reader, game.model.object.TriggerType.class,
                fields[6].trim(), "트리거 유형");
        String extra = fields[7].trim();

        return switch (type) {
            case MONSTER -> {
                requireNoExtra(reader, extra, type);
                yield new MonsterObject(id, x, y, quizId, rewardType, triggerType);
            }
            case CHEST -> {
                requireNoExtra(reader, extra, type);
                yield new ChestObject(id, x, y, quizId, rewardType, triggerType);
            }
            case MIMIC -> {
                requireNoExtra(reader, extra, type);
                yield new MimicObject(id, x, y, quizId, rewardType, triggerType);
            }
            case TRAP -> {
                requireNoExtra(reader, extra, type);
                yield new TrapObject(id, x, y, quizId, rewardType, triggerType);
            }
            case EXIT -> {
                requireNoExtra(reader, extra, type);
                yield new ExitObject(id, x, y, quizId, rewardType, triggerType);
            }
            case MOVE_PLATFORM -> {
                String[] coords = extra.split(RecordParser.LIST_DELIMITER, -1);
                if (coords.length != 2) {
                    throw reader.error("MOVE_PLATFORM의 이동 좌표는 'x,y' 형식이어야 합니다: '" + extra + "'");
                }
                int targetX = reader.parseInt(coords[0], "이동 x", 0);
                int targetY = reader.parseInt(coords[1], "이동 y", 0);
                if (targetX >= mapWidth || targetY >= mapHeight) {
                    throw reader.error("MOVE_PLATFORM 목표 좌표(" + targetX + "," + targetY + ")가 맵 범위를 벗어납니다");
                }
                yield new MovePlatformObject(id, x, y, quizId, rewardType, triggerType, targetX, targetY);
            }
        };
    }

    private static void requireNoExtra(LineReader reader, String extra, ObjectType type) {
        if (!NO_EXTRA.equals(extra)) {
            throw reader.error(type + "의 마지막 칸은 '-'여야 합니다: '" + extra + "'");
        }
    }

    private static <E extends Enum<E>> E parseEnum(LineReader reader, Class<E> enumType, String text, String label) {
        try {
            return Enum.valueOf(enumType, text);
        } catch (IllegalArgumentException e) {
            throw reader.error("알 수 없는 " + label + " '" + text + "'");
        }
    }

    private record GridPos(int x, int y) {
    }

    private static final class LineReader {

        private final String fileName;
        private final List<String> lines;
        private int nextIndex = 0;

        LineReader(String fileName, List<String> lines) {
            this.fileName = fileName;
            this.lines = lines;
        }

        String getFileName() {
            return fileName;
        }

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
            return nextLine(what).split(RecordParser.FIELD_SPLIT, -1);
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

        void requireNoMoreContent() {
            for (int i = nextIndex; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    throw errorAt(i + 1, "오브젝트 개수보다 줄이 더 많습니다");
                }
            }
        }

        DataFormatException error(String message) {
            return errorAt(Math.max(nextIndex, 1), message);
        }

        DataFormatException errorAt(int lineNo, String message) {
            return new DataFormatException(fileName, lineNo, message);
        }
    }
}