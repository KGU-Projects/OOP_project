package game.repository.file;

import game.model.Cutscene;
import game.model.CutsceneFrame;
import game.repository.CutsceneRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class FileCutsceneRepository implements CutsceneRepository {

    private static final int FIELD_COUNT = 4;
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path directory;

    public FileCutsceneRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<Cutscene> findById(String cutsceneRef) {
        if (cutsceneRef == null) {
            throw new IllegalArgumentException("컷신 ID가 null입니다");
        }
        String id = stripTxtExtension(cutsceneRef.trim());
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("올바르지 않은 컷신 ID입니다: " + cutsceneRef);
        }

        Path file = directory.resolve(id + ".txt");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        List<String> lines = TextFileIO.readLines(file);
        List<CutsceneFrame> frames = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                frames.add(parseFrame(line));
            } catch (IllegalArgumentException e) {
                throw new DataFormatException(file.getFileName().toString(), i + 1, e.getMessage());
            }
        }

        if (frames.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Cutscene(id, frames));
    }

    private static String stripTxtExtension(String value) {
        if (value.length() > 4 && value.regionMatches(true, value.length() - 4, ".txt", 0, 4)) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private static CutsceneFrame parseFrame(String line) {
        String[] f = RecordParser.splitFields(line, FIELD_COUNT);
        return new CutsceneFrame(
                f[0],
                f[1],
                f[2],
                f[3].replace("\\n", "\n"));
    }
}