package game.common;

/**
 * 게임 전체에서 쓰는 상수 모음.
 * 지금은 스테이지 로딩에 필요한 것만 있고, 물리·체력·점수 상수는 해당 기능을 만들 때 추가한다.
 */
public final class GameConstants {

    /** 스테이지 파일(stageN.txt)이 들어 있는 폴더. 프로젝트 루트에서 실행하는 것을 전제로 한다. */
    public static final String STAGE_DIRECTORY = "data/stages";

    /** 사용자당 세이브 슬롯 수. 슬롯 번호는 1부터 이 값까지다. */
    public static final int SAVE_SLOT_COUNT = 3;

    /** 플레이어 몸체 크기(타일 단위). 시작 위치 좌표 변환에 쓴다. */
    public static final double PLAYER_WIDTH = 0.8;
    public static final double PLAYER_HEIGHT = 1.6;

    private GameConstants() {
    }
}
