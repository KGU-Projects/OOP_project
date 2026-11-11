package game.repository;

import game.model.Stage;

import java.util.List;
import java.util.Optional;

/** 스테이지 읽기 전용 저장소. 스테이지는 파일을 직접 편집해서 관리한다. */
public interface StageRepository {

    /** 번호 순으로 모든 스테이지를 돌려준다. */
    List<Stage> findAll();

    Optional<Stage> findByNumber(int stageNo);
}
