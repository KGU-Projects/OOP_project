package game.model;

/** 퀴즈 채점 결과. GameService가 만들어 UI에 전달 */
public class AnswerResult {
    private final boolean correct;
    private final String explanation;
    private final int gainedScore;
    private final int damageTaken;
    private final boolean playerDead;
    private final int bossDamage;
    private final boolean bossDefeated; 

    /** 퀴즈 하나 풀고 결과 메모 */
    public AnswerResult(boolean correct, String explanation, int gainedScore, int damageTaken, boolean playerDead, int bossDamage, boolean bossDefeated) {
        this.correct = correct;
        this.explanation = explanation;
        this.gainedScore = gainedScore;
        this.damageTaken = damageTaken;
        this.playerDead = playerDead;
        this.bossDamage = bossDamage;
        this.bossDefeated = bossDefeated;
    }


    /** 정답이면 true */
    public boolean isCorrect() {
        return correct;
    }
    /** 문제 해설 */
    public String getExplanation() {
        return explanation;
    }
    /** 얻은 점수 */
    public int getGainedScore() {
        return gainedScore;
    }
    /** 깎인 체력 */
    public int getDamageTaken() {
        return damageTaken;
    }
    /** 체력이 0이 되면 true */
    public boolean isPlayerDead() {
        return playerDead;
    }
    /** 보스에게 준 데미지 */
    public int getBossDamage() {
        return bossDamage;
    }
    /** 보스를 쓰러뜨렸으면 true */
    public boolean isBossDefeated() {
        return bossDefeated;
    }
}