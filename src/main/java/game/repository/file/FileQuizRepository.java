package game.repository.file;

import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.model.quiz.QuizType;
import game.repository.QuizRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** quiz.txt를 읽는 QuizRepository 구현체. 형식은 docs/data-format.md를 따른다. 읽을 때마다 파일을 다시 읽는다. */
public class FileQuizRepository implements QuizRepository {

    private static final int FIELD_COUNT = 7; // 문제ID|스테이지|유형|문제|보기|정답|해설
    private static final String CHOICE_DELIMITER = "~";

    private final Path file;

    public FileQuizRepository(Path file) {
        this.file = file;
    }

    @Override
    public List<Quiz> findAll() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> lines = TextFileIO.readLines(file);
        List<Quiz> quizzes = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                Quiz quiz = parse(line);
                if (!ids.add(quiz.getId())) {
                    throw new IllegalArgumentException("문제ID가 중복됩니다: " + quiz.getId());
                }
                quizzes.add(quiz);
            } catch (IllegalArgumentException e) {
                // 형식 오류와 모델의 검증 실패에 파일 위치를 붙이는 곳은 여기 한 곳이다
                throw new DataFormatException(file.getFileName().toString(), i + 1, e.getMessage());
            }
        }
        return List.copyOf(quizzes);
    }

    @Override
    public Optional<Quiz> findById(int id) {
        return findAll().stream().filter(q -> q.getId() == id).findFirst();
    }

    @Override
    public void print() {
        List<Quiz> quizzes = findAll();
        System.out.println("=== 문제 (" + file + ") " + quizzes.size() + "개 ===");
        quizzes.forEach(System.out::println);
    }

    // 유형 문자열을 객체로 바꾸는 곳은 이 switch 한 곳뿐이다.
    private static Quiz parse(String line) {
        String[] f = RecordParser.splitFields(line, FIELD_COUNT);
        int id = RecordParser.parseInt(f[0], "문제ID");
        int stageNo = RecordParser.parseInt(f[1], "스테이지");
        String question = unescape(f[3]);
        String explanation = unescape(f[6]);
        return switch (parseType(f[2])) {
            case CHOICE ->
                    new ChoiceQuiz(
                            id, stageNo, question, explanation, parseChoices(f[4]),
                            RecordParser.parseInt(f[5], "정답 번호"));
            case INPUT -> {
                if (!f[4].isEmpty()) {
                    throw new IllegalArgumentException("입력형은 보기 칸이 비어 있어야 합니다");
                }
                yield new InputQuiz(id, stageNo, question, explanation, f[5]);
            }
        };
    }

    private static List<String> parseChoices(String field) {
        if (field.isEmpty()) {
            throw new IllegalArgumentException("객관식은 보기가 필요합니다");
        }
        return Arrays.stream(field.split(CHOICE_DELIMITER, -1))
                .map(FileQuizRepository::unescape)
                .toList();
    }

    private static QuizType parseType(String value) {
        try {
            return QuizType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 유형입니다: '" + value + "' (CHOICE 또는 INPUT)");
        }
    }

    /** 파일에는 줄바꿈이 \n 두 글자로 적혀 있다. */
    private static String unescape(String value) {
        return value.replace("\\n", "\n");
    }
}
