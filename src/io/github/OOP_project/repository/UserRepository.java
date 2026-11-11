package io.github.OOP_project.repository;

import io.github.OOP_project.model.User;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 저장소. users.txt에 해당한다.
 *
 * 닉네임이 키이며 중복될 수 없다. 파일에 중복이 있으면 읽을 때 DataFormatException을 던진다.
 * 오류: 형식 오류는 DataFormatException, 입출력 실패는 UncheckedIOException.
 */
public interface UserRepository {

    /** 파일에 적힌 순서대로 모든 사용자를 돌려준다. 파일이 없으면 빈 목록이다. */
    List<User> findAll();

    /** 닉네임으로 찾는다. 대소문자를 구분하며 정확히 같아야 한다. 없으면 empty. */
    Optional<User> findByNickname(String nickname);

    /**
     * 사용자 목록 전체를 파일에 덮어쓴다. 일부만 고치는 메서드는 없다.
     * 호출하는 쪽은 findAll()로 읽은 목록을 고쳐서 넘긴다.
     * 파일이나 폴더가 없으면 만든다. 닉네임이 중복된 목록은 IllegalArgumentException.
     */
    void saveAll(List<User> users);
}
