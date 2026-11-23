package game.repository;

import game.model.Cutscene;

import java.util.Optional;

public interface CutsceneRepository {

    /** 컷신 ID(파일명 확장자 제외)로 컷신을 읽는다. 파일이 없으면 empty. */
    Optional<Cutscene> findById(String cutsceneId);
}