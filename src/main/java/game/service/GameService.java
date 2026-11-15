package game.service;

import game.model.Stage;
import game.model.GameState;
import game.model.User;
import game.repository.QuizRepository;
import game.repository.StageRepository;

/** UI와 데이터 사이 연결 및 게임 규칙 처리 */
public class GameService {
    private static final int FIRST_STAGE = 1;
    /** 플레이어 최대 체력 (확정 전 임시값, 3으로 수정될 수도. 일단 UI에서 임시로 쓰는 5로 맞춤) */
    private static final int MAX_HP = 5;
    private final QuizRepository quizRepository;
    private final StageRepository stageRepository;
    private GameState state;
    private User currentUser;

    /** 게임 시작할 수 있게 데이터 받아오기*/
    public GameService(QuizRepository quizRepository, StageRepository stageRepository) {
        this.quizRepository = quizRepository;
        this.stageRepository = stageRepository;
    }

    /** 새 게임: 1스테이지를 처음 상태로 시작하기 */
    public void startNewGame(User user) {
        Stage firstStage = stageRepository.findByNumber(FIRST_STAGE)
                    .orElseThrow(() -> new IllegalStateException("1스테이지 파일이 없습니다"));
        this.currentUser = user;
        this.state = new GameState(firstStage, MAX_HP, null);
    }

    /**현재 게임 상태, UI에서 사용 */
    public GameState getState() {
        return state;
    }
}