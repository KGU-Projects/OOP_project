package main.java.game.repository;

import main.java.game.model.SaveData;

import java.util.Optional;

/**
 * 세이브 저장소. data/saves/&lt;닉네임&gt;/slotN.txt에 해당하며, 파일 하나가 슬롯 하나다.
 *
 * 슬롯 번호는 1부터 SAVE_SLOT_COUNT까지다. 범위를 벗어나면 IllegalArgumentException.
 * 닉네임은 폴더 이름으로 쓰이므로 UserService의 닉네임 규칙(한글·영문·숫자)을 지킨 값이어야 하고,
 * 그렇지 않으면 IllegalArgumentException을 던진다. 경로 문자(/, .. 등)가 들어갈 수 없게 하기 위해서다.
 * 오류: 형식 오류는 DataFormatException, 입출력 실패는 UncheckedIOException.
 */
public interface SaveRepository {

    /** 슬롯의 저장 상태를 읽는다. 파일이 없으면(빈 슬롯) empty. */
    Optional<SaveData> find(String nickname, int slot);

    /** 슬롯에 저장한다. 이미 있으면 덮어쓴다. 사용자 폴더가 없으면 만든다. */
    void save(String nickname, int slot, SaveData data);

    /** 슬롯을 비운다. 이미 비어 있으면 아무 일도 하지 않는다. */
    void delete(String nickname, int slot);
}
