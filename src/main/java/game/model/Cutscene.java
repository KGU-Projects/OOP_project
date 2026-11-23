package game.model;

import java.util.List;

/** 여러 장면(CutsceneFrame)으로 구성된 컷신 데이터. */
public class Cutscene {

    private final String id;
    private final List<CutsceneFrame> frames;

    public Cutscene(String id, List<CutsceneFrame> frames) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("컷신 ID가 비어 있습니다");
        }
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("컷신 장면이 하나 이상 필요합니다: " + id);
        }
        this.id = id.trim();
        this.frames = List.copyOf(frames);
    }

    public String getId() {
        return id;
    }

    public List<CutsceneFrame> getFrames() {
        return frames;
    }

    public int size() {
        return frames.size();
    }
}