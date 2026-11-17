package game.repository.file;

import game.model.User;
import game.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** users.txt를 읽고 쓰는 UserRepository 구현체. 형식은 docs/data-format.md를 따른다. */
public class FileUserRepository implements UserRepository {

    private static final int FIELD_COUNT = 4; // 닉네임|최고점수|클리어한 스테이지 수|마지막 플레이 일시

    private final Path file;

    public FileUserRepository(Path file) {
        this.file = file;
    }

    @Override
    public List<User> findAll() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> lines = TextFileIO.readLines(file);
        List<User> users = new ArrayList<>();
        Set<String> nicknames = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            try {
                User user = parse(line);
                if (!nicknames.add(user.getNickname())) {
                    throw new IllegalArgumentException("닉네임이 중복됩니다: " + user.getNickname());
                }
                users.add(user);
            } catch (IllegalArgumentException e) {
                // 형식 오류와 모델의 검증 실패에 파일 위치를 붙이는 곳은 여기 한 곳이다
                throw new DataFormatException(file.getFileName().toString(), i + 1, e.getMessage());
            }
        }
        return List.copyOf(users);
    }

    @Override
    public Optional<User> findByNickname(String nickname) {
        return findAll().stream().filter(u -> u.getNickname().equals(nickname)).findFirst();
    }

    @Override
    public void saveAll(List<User> users) {
        Set<String> nicknames = new HashSet<>();
        List<String> lines = new ArrayList<>();
        for (User user : users) {
            if (!NicknameRules.isValid(user.getNickname())) {
                throw new IllegalArgumentException("닉네임은 1~12자의 한글, 영문, 숫자여야 합니다: " + user.getNickname());
            }
            if (!nicknames.add(user.getNickname())) {
                throw new IllegalArgumentException("닉네임이 중복됩니다: " + user.getNickname());
            }
            if (user.getLastPlayedAt() == null) {
                throw new IllegalArgumentException("마지막 플레이 일시가 없습니다: " + user.getNickname());
            }
            lines.add(format(user));
        }
        TextFileIO.writeLines(file, lines);
    }

    @Override
    public void print() {
        List<User> users = findAll();
        System.out.println("=== 사용자 (" + file + ") " + users.size() + "명 ===");
        users.forEach(System.out::println);
    }

    private static User parse(String line) {
        String[] f = RecordParser.splitFields(line, FIELD_COUNT);
        if (!NicknameRules.isValid(f[0])) {
            throw new IllegalArgumentException("닉네임은 1~12자의 한글, 영문, 숫자여야 합니다: '" + f[0] + "'");
        }
        return new User(
                f[0],
                RecordParser.parseInt(f[1], "최고점수"),
                RecordParser.parseInt(f[2], "클리어한 스테이지 수"),
                RecordParser.parseDateTime(f[3]));
    }

    private static String format(User user) {
        return String.join(
                RecordParser.FIELD_JOIN,
                user.getNickname(),
                String.valueOf(user.getHighScore()),
                String.valueOf(user.getClearedStages()),
                user.getLastPlayedAt().format(RecordParser.DATE_TIME));
    }
}
