package game.model;

/** 컷신의 한 장면(배경 이미지, 화자 초상화 이미지, 화자 이름, 대사). */
public record CutsceneFrame(String backgroundImage, String speakerImage, String speaker, String dialogue) {

    public CutsceneFrame {
        if (dialogue == null || dialogue.isBlank()) {
            throw new IllegalArgumentException("컷신 대사가 비어 있습니다");
        }
        backgroundImage = normalizeOptional(backgroundImage);
        speakerImage = normalizeOptional(speakerImage);
        speaker = (speaker == null || speaker.isBlank() || "-".equals(speaker.trim()))
                ? ""
                : speaker.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        return value.trim();
    }

    public boolean hasBackgroundImage() {
        return backgroundImage != null;
    }

    public boolean hasSpeakerImage() {
        return speakerImage != null;
    }

    public boolean hasSpeaker() {
        return !speaker.isEmpty();
    }
}