package game.service;

import game.repository.UserRepository;
import game.model.User;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

/** 사용자 등록, 닉네임 검사, 순위, 클리어 기록 */
public class UserService {
    private static final String NICKNAME_RULE = "[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{1,12}";
    private final UserRepository userRepository;

    /** 사용자 창고(장부 읽고 쓰는 곳) 받아두기 */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 모든 사용자 목록 */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /** 점수로 사용자 정렬(순위 화면에서 사용) */
    public List<User> getRanking() {
        List<User> users = new ArrayList<>(findAll());
        List<User> ranking = new ArrayList<>();

        while (!users.isEmpty()) {
            User best = users.get(0);
            for (User user : users) {
                if (user.getHighScore() > best.getHighScore()) {
                    best = user;
                }
            }
            ranking.add(best);
            users.remove(best);
        }
        return ranking;
    }

    /**
     * 닉네임 검사: 1~12자의 한글·영문·숫자인지, 이미 있는 닉네임인지 확인한다. 안 되면 IllegalArgumentException.
     * 규칙은 repository/file/NicknameRules와 같다. NicknameRules.isValid가 public이 아니라 service 패키지에서
     * 부를 수 없어서 같은 규칙을 NICKNAME_RULE로 따로 둠. 규칙을 바꿀 땐 두 곳 다 고쳐야 함.
     * TODO: NicknameRules 클래스와 isValid가 public이 되면
     *  1. 규칙 검사를 !NicknameRules.isValid(nickname)으로 바꾸고 (isValid가 null도 검사함)
     *  2. NICKNAME_RULE 상수를 지우고(11번째 줄)
     *  3. import game.repository.file.NicknameRules; 추가
     */
    public void validateNickname(String nickname) {
        // 규칙 검사(1~12자, 한글/영문/숫자만 사용)
        if (nickname == null || !nickname.matches(NICKNAME_RULE)) {
            throw new IllegalArgumentException("닉네임은 1~12자의 한글/영문/숫자만 쓸 수 있습니다");
        }

        // 중복 검사
        if (userRepository.findByNickname(nickname).isPresent()) {
            throw new IllegalArgumentException("이미 있는 닉네임입니다");
        }
    }

    /** 새 회원 만들기 -> 사용자 리스트에 저장 */
    public User register(String nickname) {
        validateNickname(nickname);

        User newUser = User.createNew(nickname, LocalDateTime.now());
        List<User> users = new ArrayList<>(findAll());
        users.add(newUser);
        userRepository.saveAll(users);
        
        return newUser;
    }

    /** 한 스테이지 다 깼을 때 사용자 리스트에 기록 */
    public void recordStageClear(User user, int score) {
        List<User> users = new ArrayList<>(findAll());

        // for-each: int가 아니면 주소가 복사라 수정 가능
        for (User saved : users) {
            if (saved.getNickname().equals(user.getNickname())) {
                // User의 recordStageClear (이름만 같고 다른 메서드): 최고 점수(이전 기록보다 더 높을 때만), 클리어 수 +1, 마지막 플레이 시간 갱신
                saved.recordStageClear(score, LocalDateTime.now());
            }
        }
        userRepository.saveAll(users);
    }

}