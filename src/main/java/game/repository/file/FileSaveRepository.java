package game.repository.file;

import game.common.GameConstants;
import game.common.NicknameRules;
import game.model.ItemType;
import game.model.SaveData;
import game.repository.SaveRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** data/saves/&lt;닉네임&gt;/slotN.txt를 읽고 쓰는 SaveRepository 구현체. 형식은 docs/data-format.md를 따른다. */
public class FileSaveRepository implements SaveRepository {

    private static final int LINE_NO = 1; // 파일 한 개에 한 줄이라 오류 위치는 항상 1줄이다
    private static final int FIELD_COUNT = 5; // 마지막 클리어 스테이지|체력|점수|인벤토리|저장 일시
    private static final String COUNT_DELIMITER = ":";

    private final Path directory;

    public FileSaveRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<SaveData> find(String nickname, int slot) {
        Path file = slotFile(nickname, slot);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String fileName = nickname + "/" + file.getFileName();
        List<String> lines = TextFileIO.readLines(file);
        List<String> contents = lines.stream().filter(l -> !l.isBlank()).toList();
        try {
            if (contents.size() != 1) {
                throw new IllegalArgumentException("저장 상태는 한 줄이어야 합니다 (" + contents.size() + "줄)");
            }
            return Optional.of(parse(contents.get(0)));
        } catch (IllegalArgumentException e) {
            // 형식 오류와 모델의 검증 실패에 파일 위치를 붙이는 곳은 여기 한 곳이다
            throw new DataFormatException(fileName, LINE_NO, e.getMessage());
        }
    }

    @Override
    public void save(String nickname, int slot, SaveData data) {
        Path file = slotFile(nickname, slot);
        if (data.getSavedAt() == null) {
            throw new IllegalArgumentException("저장 일시가 없습니다");
        }
        TextFileIO.writeLines(file, List.of(format(data)));
    }

    @Override
    public void delete(String nickname, int slot) {
        Path file = slotFile(nickname, slot);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("세이브 파일을 지울 수 없습니다: " + file, e);
        }
    }

    @Override
    public void print(String nickname) {
        System.out.println("=== 세이브 (" + directory.resolve(nickname) + ") ===");
        for (int slot = 1; slot <= GameConstants.SAVE_SLOT_COUNT; slot++) {
            String content = find(nickname, slot).map(SaveData::toString).orElse("(빈 슬롯)");
            System.out.println("슬롯 " + slot + " | " + content);
        }
    }

    // 닉네임과 슬롯 번호를 검사한 뒤에만 경로를 만든다. 경로 문자가 끼어들 수 없다.
    private Path slotFile(String nickname, int slot) {
        if (!NicknameRules.isValid(nickname)) {
            throw new IllegalArgumentException("닉네임은 1~12자의 한글, 영문, 숫자여야 합니다: " + nickname);
        }
        if (slot < 1 || slot > GameConstants.SAVE_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "슬롯 번호는 1~" + GameConstants.SAVE_SLOT_COUNT + "이어야 합니다: " + slot);
        }
        return directory.resolve(nickname).resolve("slot" + slot + ".txt");
    }

    private static SaveData parse(String line) {
        String[] f = RecordParser.splitFields(line, FIELD_COUNT);
        return new SaveData(
                RecordParser.parseInt(f[0], "마지막 클리어 스테이지"),
                RecordParser.parseInt(f[1], "체력"),
                RecordParser.parseInt(f[2], "점수"),
                parseInventory(f[3]),
                RecordParser.parseDateTime(f[4]));
    }

    private static Map<ItemType, Integer> parseInventory(String field) {
        Map<ItemType, Integer> inventory = new EnumMap<>(ItemType.class);
        if (field.isBlank()) {
            return inventory;
        }
        for (String entry : field.split(RecordParser.LIST_DELIMITER, -1)) {
            String[] pair = entry.split(COUNT_DELIMITER, -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("인벤토리 항목이 '종류:개수' 형식이 아닙니다: '" + entry + "'");
            }
            ItemType type;
            try {
                type = ItemType.valueOf(pair[0].trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("알 수 없는 아이템 종류입니다: '" + pair[0] + "'");
            }
            int count = RecordParser.parseInt(pair[1], "아이템 개수");
            if (count < 1) {
                throw new IllegalArgumentException("아이템 개수는 1 이상이어야 합니다: " + entry);
            }
            if (inventory.put(type, count) != null) {
                throw new IllegalArgumentException("아이템 종류가 중복됩니다: " + type);
            }
        }
        return inventory;
    }

    private static String format(SaveData data) {
        List<String> items = new ArrayList<>();
        // EnumMap이 아니어도 ItemType 선언 순서로 쓰도록 values()를 돈다
        for (ItemType type : ItemType.values()) {
            int count = data.getInventory().getOrDefault(type, 0);
            if (count > 0) {
                items.add(type.name() + COUNT_DELIMITER + count);
            }
        }
        return String.join(
                RecordParser.FIELD_JOIN,
                String.valueOf(data.getClearedStage()),
                String.valueOf(data.getHp()),
                String.valueOf(data.getScore()),
                String.join(RecordParser.LIST_DELIMITER, items),
                data.getSavedAt().format(RecordParser.DATE_TIME));
    }
}
