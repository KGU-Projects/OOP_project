package io.github.OOP_project.tool;

import io.github.OOP_project.common.GameConstants;
import io.github.OOP_project.model.Stage;
import io.github.OOP_project.repository.StageRepository;
import io.github.OOP_project.repository.file.DataFormatException;
import io.github.OOP_project.repository.file.FileStageRepository;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 개발용 스테이지 미리보기. 게임이 아니므로 GameApp을 거치지 않고 파일 구현체를 직접 만든다.
 *
 * 사용: java -cp out io.github.OOP_project.tool.StagePreviewMain [스테이지 번호]
 * 번호를 생략하면 모든 스테이지를 출력한다. 프로젝트 루트에서 실행한다.
 */
public class StagePreviewMain {

    private static final int EXIT_DATA_ERROR = 1;
    private static final int EXIT_USAGE_ERROR = 2;

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        StageRepository repository = new FileStageRepository(Path.of(GameConstants.STAGE_DIRECTORY));
        StagePreview preview = new StagePreview();

        List<Stage> stages;
        try {
            stages = selectStages(repository, args);
        } catch (NumberFormatException e) {
            System.err.println("스테이지 번호는 정수여야 합니다: " + args[0]);
            System.exit(EXIT_USAGE_ERROR);
            return;
        } catch (DataFormatException e) {
            // 파일 이름과 줄 번호가 들어 있는 메시지를 그대로 보여 준다
            System.err.println("데이터 오류: " + e.getMessage());
            System.exit(EXIT_DATA_ERROR);
            return;
        }

        if (stages.isEmpty()) {
            System.err.println("출력할 스테이지가 없습니다. 폴더: " + GameConstants.STAGE_DIRECTORY);
            System.exit(EXIT_DATA_ERROR);
            return;
        }
        for (Stage stage : stages) {
            out.println(preview.render(stage));
        }
    }

    private static List<Stage> selectStages(StageRepository repository, String[] args) {
        if (args.length == 0) {
            return repository.findAll();
        }
        Optional<Stage> stage = repository.findByNumber(Integer.parseInt(args[0]));
        return stage.map(List::of).orElse(List.of());
    }
}
