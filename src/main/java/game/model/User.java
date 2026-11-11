package game.model;

import java.time.LocalDateTime;

/**
 * 사용자. users.txt의 한 줄에 대응한다.
 * 진행 상태(스테이지, 위치, 체력 등)는 User가 아니라 SaveData가 가진다.
 */
public class User {

    private final String nickname;
    private int highScore;           // 어느 플레이에서든 기록한 최고 점수
    private int clearedStages;       // 클리어한 스테이지 누적 수
    private LocalDateTime lastPlayedAt;

    public User(String nickname, int highScore, int clearedStages, LocalDateTime lastPlayedAt) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임이 비어 있습니다");
        }
        if (highScore < 0 || clearedStages < 0) {
            throw new IllegalArgumentException("점수와 클리어 수는 0 이상이어야 합니다 (" + nickname + ")");
        }
        this.nickname = nickname;
        this.highScore = highScore;
        this.clearedStages = clearedStages;
        this.lastPlayedAt = lastPlayedAt;
    }

    /** 새 사용자: 기록이 없고 마지막 플레이 시각은 만든 시각이다. */
    public static User createNew(String nickname, LocalDateTime now) {
        return new User(nickname, 0, 0, now);
    }

    /** 스테이지를 클리어했을 때 기록을 갱신한다. 최고점수는 더 높을 때만 바뀐다. */
    public void recordStageClear(int score, LocalDateTime at) {
        this.highScore = Math.max(this.highScore, score);
        this.clearedStages++;
        this.lastPlayedAt = at;
    }

    public void updateLastPlayedAt(LocalDateTime at) {
        this.lastPlayedAt = at;
    }

    public String getNickname() {
        return nickname;
    }

    public int getHighScore() {
        return highScore;
    }

    public int getClearedStages() {
        return clearedStages;
    }

    public LocalDateTime getLastPlayedAt() {
        return lastPlayedAt;
    }
}
