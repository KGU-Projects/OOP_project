package game.repository.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** 텍스트 데이터 파일 입출력 도우미. 인코딩은 항상 UTF-8로 명시한다. */
public final class TextFileIO {

    private static final char BOM = '﻿';

    private TextFileIO() {
    }

    /** 파일의 모든 줄을 읽는다. 윈도우 편집기가 붙이는 BOM은 제거한다. */
    public static List<String> readLines(Path path) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            if (!lines.isEmpty() && !lines.get(0).isEmpty() && lines.get(0).charAt(0) == BOM) {
                lines.set(0, lines.get(0).substring(1));
            }
            return lines;
        } catch (IOException e) {
            // UTF-8이 아닌 파일도 여기로 온다. 원인을 감춰 두지 않고 경로와 함께 다시 던진다.
            throw new UncheckedIOException("파일을 읽을 수 없습니다(UTF-8 확인): " + path, e);
        }
    }

    /** 줄들을 UTF-8로 파일에 덮어쓴다. 부모 폴더가 없으면 만든다. 임시 파일에 먼저 쓴 뒤 옮겨서, 쓰다가 실패해도 원래 파일이 깨지지 않게 한다. */
    public static void writeLines(Path path, List<String> lines) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                Files.write(temp, lines, StandardCharsets.UTF_8);
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓸 수 없습니다: " + path, e);
        }
    }
}
