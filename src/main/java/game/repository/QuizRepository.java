package game.repository;

import game.model.quiz.Quiz;

import java.util.List;
import java.util.Optional;

/**
 * 문제 읽기 전용 저장소. 문제는 quiz.txt를 직접 편집해서 관리한다.
 *
 * 오류: 데이터 형식이 맞지 않으면 DataFormatException(파일 이름, 줄 번호 포함),
 * 입출력에 실패하면 UncheckedIOException을 던진다.
 */
public interface QuizRepository {

    /** 파일에 적힌 순서대로 모든 문제를 돌려준다. 파일이 없으면 빈 목록이다. */
    List<Quiz> findAll();

    /** 문제ID로 찾는다. 없으면 empty. */
    Optional<Quiz> findById(int id);

    /** 개발용: 읽은 내용을 보기 좋게 콘솔에 출력한다. 저장소가 파일을 제대로 읽는지 확인할 때 쓴다. */
    void print();
}
