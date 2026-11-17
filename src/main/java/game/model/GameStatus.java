package game.model;

/** 게임의 현재 단계 */
public enum GameStatus {
    /** 맵을 돌아다니는 중 */
    PLAYING,
    QUIZ,
    GAME_OVER,
    /** 보스를 쓰러뜨려 게임 클리어, 엔딩 화면 */
    CLEARED
}