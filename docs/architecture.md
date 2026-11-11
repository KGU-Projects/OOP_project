# 클래스 설계

## 패키지 구조

기본 패키지는 `io.github.OOP_project`이고, 소스는 `src/io/github/OOP_project/`에 있다. 아래에서 `[현재]`는 이미 있는 것, `[예정]`은 새로 만들 것이다.

```
io.github.OOP_project
  GameApp                     [현재] main, 객체 조립
  common/                     [현재] ScreenType, GameConstants(스테이지 폴더·플레이어 크기만)  [예정] 물리·체력·점수 상수 추가
  model/                      [예정] User, SaveData, Stage, Tile, Player, Inventory, ItemType,
                                     GameState, GameStatus, AnswerResult, InteractionResult
    object/                   [예정] GameObject(추상), ObjectType,
                                     ButtonObject, DoorObject, KeyObject, ItemObject, BossObject
    quiz/                     [예정] Quiz(추상), ChoiceQuiz, InputQuiz, QuizType
  repository/                 [예정] UserRepository, QuizRepository, StageRepository, SaveRepository (인터페이스)
    file/                     [예정] TextFileIO, File*Repository, DataFormatException
    memory/                   [예정] InMemory*Repository (개발용)
  service/                    [현재] UserService(임시)  [예정] GameService, SaveService, PhysicsEngine
  tool/                       [현재] StagePreview, StagePreviewMain (개발용 맵 미리보기, 게임 실행과 무관)
  ui/                         [현재] Screen, ScreenManager
    screens/                  [현재] StartScreen  [예정] 나머지 화면
    components/               [예정] 맵 패널, HUD 등 재사용 컴포넌트
```

- 의존 방향: `ui → service → repository 인터페이스 ← repository.file`. 모든 계층이 `model`과 `common`을 사용한다.
- `ui`는 파일이나 repository를 직접 다루지 않는다. `model`은 Swing, 파일 입출력, service를 모른다.
- 구현체 생성과 연결은 `GameApp`에서만 한다. 개발용 도구인 `tool/StagePreviewMain`만 예외로, 파일 구현체를 직접 만든다.

## Quiz 계층

```java
public abstract class Quiz {
    private final int id;
    private final int stageNo;
    private final String question;
    private final String explanation;

    public abstract QuizType getType();
    public abstract boolean isCorrect(String input);
}
```

- `ChoiceQuiz`: 보기 목록과 정답 번호. 입력 번호와 비교한다.
- `InputQuiz`: 정답 문자열. 공백 제거, 대소문자 무시 후 비교한다.
- 유형 문자열을 객체로 바꾸는 코드는 `FileQuizRepository`의 팩토리 메서드 한 곳에만 둔다.
- 문제 유형별 분기(`instanceof`, 유형 `if`)를 하지 않고 `quiz.isCorrect(input)`을 쓴다.

## GameObject 계층

맵 위의 상호작용 대상을 하나의 추상 클래스로 다룬다. 새 장애물이나 아이템 종류는 하위 클래스를 추가하는 것으로 늘린다.

```java
public abstract class GameObject {
    private final String id;      // 스테이지 안에서 유일
    private final int x;          // 왼쪽 위 타일 좌표
    private final int y;

    public abstract ObjectType getType();
    public abstract String describe();                       // [현재] 미리보기 범례용 설명
    public List<String> getLinkedIds()  { return List.of(); }        // [현재] 가리키는 오브젝트 ID (버튼 → 문)
    public Optional<ObjectType> getLinkedType() { return Optional.empty(); } // [현재] 가리키는 대상의 종류 (데이터 검증용)
    public int getWidth()  { return 1; }
    public int getHeight() { return 1; }

    // 아래 세 메서드는 [예정]: GameState를 만들 때 추가한다.
    // 플레이어가 지나갈 수 없는가. 문은 열리기 전까지 true
    public boolean isSolid(GameState state) { return false; }

    // E 키 상호작용. 무슨 일이 일어나야 하는지를 결과로 돌려준다.
    public abstract InteractionResult interact(GameState state);

    // 이 오브젝트가 요구한 퀴즈를 맞혔을 때 호출. elapsedMillis는 풀이 시간
    public void onQuizSolved(GameState state, long elapsedMillis) {}
}
```

현재 구현된 `GameObject`는 데이터와 `describe`, 연결 정보까지이고, 게임 규칙 메서드(`isSolid`, `interact`, `onQuizSolved`)는 `GameState`, `InteractionResult`가 생길 때 추가한다. `ObjectType`은 미리보기용 글자(`getSymbol`)도 가진다. `ItemType`도 지금은 `HEALTH_POTION` 이름만 있고 `use(Player)`는 `Player`를 만들 때 추가한다.

| 클래스         | `isSolid`          | `interact`                                          | `onQuizSolved`                                               |
| -------------- | ------------------ | --------------------------------------------------- | ------------------------------------------------------------ |
| `ButtonObject` | false              | 아직 안 풀었으면 퀴즈 요청(`quizId`), 풀었으면 없음 | 연결된 문 ID를 `openedDoorIds`에 추가, 자신을 푼 것으로 기록 |
| `DoorObject`   | 열리기 전까지 true | 없음                                                | -                                                            |
| `KeyObject`    | false              | 열쇠 획득, 스테이지 클리어 결과                     | -                                                            |
| `ItemObject`   | false              | 인벤토리에 추가하고 획득 기록, 주웠다는 결과        | -                                                            |
| `BossObject`   | true               | 보스 퀴즈 요청                                      | 풀이 시간으로 데미지를 계산해 보스 체력 감소                 |

- 오브젝트는 변하지 않는 정의다. 열린 문, 푼 버튼, 주운 아이템 같은 변하는 상태는 `GameState`의 ID 집합에 둔다. 그래서 스테이지를 다시 불러오지 않고도 초기화·저장할 수 있다.
- `InteractionResult`는 `NONE`, `QUIZ(quizId)`, `BOSS_QUIZ`, `PICKED_UP`, `STAGE_CLEARED` 중 하나를 담는다. `GameService`는 오브젝트 종류를 확인하지 않고 이 결과만 처리한다.
- 보스 데미지 공식은 `BossObject`가 가진 자기 규칙이다. `calculateDamage(long elapsedMillis)`는 `GameState`와 무관하므로 단독으로 테스트할 수 있다.
- 종류 문자열을 객체로 바꾸는 코드는 `FileStageRepository`의 팩토리 메서드 한 곳에만 둔다.

## 아이템과 인벤토리

```java
public enum ItemType {
    HEALTH_POTION {
        @Override public boolean use(Player player) { /* 체력 회복, 최대 체력 이내 */ }
    };

    public abstract boolean use(Player player);
}
```

- `Inventory`는 `ItemType`별 개수를 가지며 `Player`가 소유한다.
- 새 아이템은 `ItemType` 값 하나와 `use` 구현만 추가한다. `GameService.useItem`은 `ItemType`을 분기하지 않는다.

## Repository 인터페이스

```java
public interface QuizRepository {
    List<Quiz> findAll();
    Optional<Quiz> findById(int id);
}

public interface UserRepository {
    List<User> findAll();
    Optional<User> findByNickname(String nickname);
    void saveAll(List<User> users);
}

public interface StageRepository {
    List<Stage> findAll();                       // 번호 순
    Optional<Stage> findByNumber(int stageNo);
}

public interface SaveRepository {
    Optional<SaveData> find(String nickname, int slot);
    void save(String nickname, int slot, SaveData data);
    void delete(String nickname, int slot);
}
```

- `QuizRepository`는 읽기 전용이다. 문제는 `quiz.txt`를 직접 편집해서 관리한다.
- `StageRepository`도 읽기 전용이다. 스테이지는 `data/stages/stageN.txt`를 직접 편집해서 관리한다.
- `SaveRepository`는 슬롯 하나를 읽고 쓴다. 슬롯 목록은 `SaveService`가 `1..SAVE_SLOT_COUNT`를 반복해 만든다.
- `User`에서 진행 상태가 빠지고 `SaveData`가 생겼으므로, `UserRepository`의 시그니처는 그대로여도 다루는 필드가 바뀐다. 형식은 `data-format.md`를 따른다.
- 파일 구현이 끝나기 전에는 `InMemory...Repository`로 대체해 개발한다.

## Screen과 화면 전환

```java
public interface Screen {
    JPanel getPanel();
    void onEnter();   // 보일 때: 데이터 새로고침, 포커스 요청, 타이머 시작
    void onExit();    // 사라질 때: 타이머 정지
}
```

현재 코드는 `onEnter`, `onExit`을 `default` 없이 선언하고, `ScreenManager.show`가 전환할 때 둘을 호출한다. 구현할 것이 없는 화면은 빈 메서드를 둔다. `default`로 바꿀지는 합의가 필요하다.

- `ScreenManager`(`ui` 패키지)는 `CardLayout`으로 화면을 관리하고 `show(ScreenType)`으로 전환한다. `ScreenType`은 `common` 패키지에 있다.
- 화면끼리 직접 생성·참조하지 않고 `screenManager.show(ScreenType.X)`로만 전환한다.
- 화면 간에 넘길 상태(현재 사용자, 진행 중인 게임)는 service가 보관한다.

| ScreenType | 클래스           | 내용                                                 | 상태         |
| ---------- | ---------------- | ---------------------------------------------------- | ------------ |
| START      | `StartScreen`    | 시작, 불러오기, 점수판, 종료                         | 현재         |
| SETUP      | `SetupScreen`    | 닉네임 입력, 새 게임 시작                            | 현재(값만)   |
| LOAD       | `LoadScreen`     | 사용자 목록 → 저장 슬롯 목록, 선택 시 이어하기       | 현재(값만)   |
| RANKING    | `RankingScreen`  | 최고점수 순위                                        | 현재(값만)   |
| GAME       | `GameScreen`     | 맵, 플레이어, 오브젝트, HUD(체력·인벤토리·열쇠·점수) | 현재(값만)   |
| PAUSE      | `PauseScreen`    | 계속하기, 저장(슬롯 선택), 시작 화면으로             | 현재(값만)   |
| QUIZ       | `QuizScreen`     | 객관식·입력형 문제, 취소 버튼                        | 현재(값만)   |
| GAME_OVER  | `GameOverScreen` | 처음부터 시작, 마지막 저장 위치에서 시작             | 값 추가 필요 |
| ENDING     | `EndingScreen`   | 게임 클리어, 최종 점수, 순위 화면으로                | 값 추가 필요 |

"현재(값만)"은 `ScreenType`에 값이 있고 클래스는 아직 없다는 뜻이다. `GAME_OVER`, `ENDING`은 `ScreenType`에 새로 넣어야 한다. 이 파일은 공유 계약이므로 합의 후에 바꾼다.

## 서비스

### GameService 주요 메서드

```java
public void startNewGame(User user);
public void continueGame(User user, int slot);             // SaveService로 불러와 이어하기
public GameState getState();

// 입력과 한 틱
public void setMoveInput(int dir);                          // -1 왼쪽, 0 정지, 1 오른쪽
public void requestJump();
public void update();                                       // 고정 간격 한 틱: 이동, 중력, 충돌

// 상호작용과 퀴즈
public Optional<Quiz> interact();                           // 퀴즈가 필요하면 문제 반환, 풀이 시간 측정 시작
public AnswerResult submitAnswer(String input);             // 판정, 체력·점수·오브젝트 반영
public void cancelQuiz();                                   // 페널티 없이 맵으로 복귀

// 아이템
public boolean useItem(int inventoryIndex);

// 게임 오버와 저장
public boolean canRestartFromSave();
public void restartStage();                                 // 현재 스테이지 처음부터
public boolean restartFromSave();                           // 마지막 저장 슬롯으로
public void save(int slot);
```

- `GameStatus`(`PLAYING`, `QUIZ`, `GAME_OVER`, `CLEARED`)를 `GameState`가 갖는다. 화면은 `onEnter`에서 상태를 보고 `GAME_OVER`나 `ENDING`으로 전환한다.
- 열쇠를 얻으면 스테이지 클리어를 처리한다: 점수 가산, `UserService`로 최고점수·클리어 수 갱신, 다음 스테이지 로드. 모든 일반 스테이지의 열쇠를 모았으면 다음은 보스 스테이지다.
- 퀴즈 풀이 시간은 `interact()`가 퀴즈를 돌려주는 시점부터 `submitAnswer()`까지 `System.nanoTime()`으로 잰다. 게임 타이머가 멈춰도 영향이 없다.
- 보스 퀴즈는 `quiz.txt`에서 `stageNo`가 보스 스테이지 번호인 문제 중 아직 안 낸 것을 무작위로 고른다. 다 냈으면 다시 섞는다.
- 오답이면 `GameConstants.WRONG_ANSWER_DAMAGE`만큼 `Player` 체력을 깎고, 0이 되면 `GameStatus.GAME_OVER`로 바꾼다.
- `restartStage()`는 체력을 최대로 채우고, 점수·인벤토리를 `GameState`의 스테이지 입장 시점 값으로 되돌리고, 문·버튼·아이템 기록을 비우고, 플레이어를 시작 위치로 옮긴다.
- `restartFromSave()`는 `GameState`가 기억하는 마지막 슬롯(`currentSlot`)을 다시 불러온다. `0`이면 저장한 적이 없으므로 `canRestartFromSave()`가 false다.

`AnswerResult`: `correct`, `explanation`, `damageTaken`, `bossDamage`, `gainedScore`, `playerDead`, `bossDefeated`.

### SaveService 주요 메서드

```java
public List<Optional<SaveData>> findSlots(User user);       // 길이 SAVE_SLOT_COUNT, 빈 슬롯은 empty
public void save(User user, int slot, GameState state);     // GameState → SaveData
public GameState load(User user, int slot);                 // SaveData + StageRepository → GameState
```

`SaveRepository`와 `StageRepository`에만 의존한다.

### UserService 주요 메서드

```java
public User register(String nickname);                      // 검증 후 users.txt에 추가
public void validateNickname(String nickname);              // 1~12자, 한글·영문·숫자, 중복 불가
public List<User> findAll();
public List<User> getRanking();                             // 최고점수 내림차순
public void recordStageClear(User user, int score);         // 최고점수 갱신, 클리어 수 +1, 마지막 플레이 일시 갱신
```

현재 코드의 `registerUser(String)`은 임시 구현이다. 위 시그니처로 교체한다.

### PhysicsEngine

`PhysicsEngine`은 `GameState`를 받아 한 틱 동안의 이동을 계산하는 순수 계산 클래스다. Swing과 파일을 모른다.

- 단위는 타일이고, 시간 간격은 `GameConstants.TICK_SECONDS`로 고정한다.
- 가로 이동과 세로 이동을 따로 계산해 충돌 면에서 멈춘다. 충돌 대상은 `Stage`의 `#` 타일과 `isSolid(state)`가 true인 오브젝트(닫힌 문, 보스)다.
- 바닥에 닿아 있을 때만 점프할 수 있다.
- 상호작용 대상은 플레이어 몸체에서 `INTERACT_RANGE` 이내에 있는 오브젝트 중 가장 가까운 것이다.

## 게임 루프와 입력

- 갱신과 그리기는 `javax.swing.Timer`(약 16ms)로 처리한다. `Thread.sleep` 루프를 쓰지 않는다. 타이머가 `gameService.update()`를 한 번 부르고 `repaint()`한다.
- 키 입력은 Key Bindings(`InputMap`/`ActionMap`)를 사용한다. 이동 키는 누름과 뗌을 모두 바인딩해 `setMoveInput`에 넘긴다.
- `GameScreen.onExit()`에서 타이머를 멈추고 `onEnter()`에서 다시 시작한다. 퀴즈 화면과 정지 화면으로 갈 때도 같다.
- 픽셀 크기(`TILE_SIZE_PX`)는 `ui`에서만 쓴다. `model`과 `service`는 타일 단위만 쓴다.

## 스테이지 미리보기 도구

스테이지 파일은 지형(글자 격자)과 오브젝트(좌표 목록)가 나뉘어 있어서 파일만 봐서는 오브젝트의 위치를 알기 어렵다. 그래서 두 정보를 겹쳐 글자 그림으로 출력하는 개발용 도구를 둔다. 파일 형식은 바뀌지 않는다.

```
tool/StagePreview       Stage를 받아 미리보기 문자열을 만든다. 파일을 읽지 않는다.
tool/StagePreviewMain   main. 스테이지 파일을 읽어 StagePreview로 출력한다.
```

```bash
java -cp out io.github.OOP_project.tool.StagePreviewMain 3     # 3스테이지
java -cp out io.github.OOP_project.tool.StagePreviewMain       # 모든 스테이지
```

- 파일 읽기는 `FileStageRepository`를 재사용한다. 도구가 따로 파싱하지 않으므로 게임이 읽는 방식 그대로 검사되고, 형식 오류는 파일 이름과 줄 번호를 포함한 `DataFormatException` 메시지로 나온다. 미리보기가 곧 데이터 검증 도구다.
- `StagePreview`는 `Stage`만 알고 파일을 모른다. 그래서 `InMemoryStageRepository`로 만든 `Stage`에도 쓸 수 있다.
- `StagePreviewMain`은 게임이 아니므로 `GameApp`을 거치지 않고 `FileStageRepository`를 직접 만든다. 이것이 "구현체 연결은 `GameApp`에서만"의 유일한 예외다.

출력은 격자의 좌표 눈금, 오브젝트를 겹친 지도, 오브젝트 범례로 이뤄진다.

```
스테이지 1 "변수의 방" (변수, NORMAL) 20 x 6

      x: 01234567890123456789
 y=0     ####################
 y=1     #........D.........#
 y=2     #.....I..D.........#
 y=3     #....###.D.........#
 y=4     #P.B.....D........K#
 y=5     ####################

 B  b1     (3, 4)     버튼  문제 1 → d1
 I  p1     (6, 2)     아이템  HEALTH_POTION x1
 D  d1     (9, 1)     문  높이 4
 K  k1     (18, 4)    열쇠

 # 단단한 블록, . 빈 공간, P 시작 위치, * 겹친 칸(확인 필요)
```

| 글자 | 의미     | 글자 | 의미              |
| ---- | -------- | ---- | ----------------- |
| `#`  | 단단한 블록 | `P`  | 플레이어 시작 위치 |
| `.`  | 빈 공간  | `B`  | 버튼              |
| `D`  | 문       | `K`  | 열쇠              |
| `I`  | 아이템   | `M`  | 보스              |
| `*`  | 오브젝트가 겹친 칸 (경고) | | |

- 이 글자는 미리보기 출력용이고 스테이지 파일에는 쓰지 않는다. 격자에 `B` 같은 글자를 넣으면 `FileStageRepository`가 알 수 없는 지형 문자로 오류를 낸다.
- 여러 칸을 차지하는 오브젝트(문)는 차지하는 칸마다 글자를 그린다.
- 지형 벽 안에 파묻힌 오브젝트, 같은 칸에 겹친 오브젝트는 `*`로 표시해 눈에 띄게 한다.
- 범례의 ID와 좌표로 파일의 오브젝트 줄을 바로 찾을 수 있다.

## 상수 (초기안, 팀이 조정)

`common/GameConstants`에 모은다. 매직 넘버를 코드에 직접 쓰지 않는다.

| 상수                            | 초기값   | 설명                          |
| ------------------------------- | -------- | ----------------------------- |
| `TICK_MILLIS`                   | 16       | 타이머 간격                   |
| `GRAVITY`                       | 40.0     | 타일/초²                      |
| `MOVE_SPEED`                    | 6.0      | 타일/초                       |
| `JUMP_SPEED`                    | 14.0     | 타일/초. 점프 높이 약 2.4타일 |
| `PLAYER_WIDTH`, `PLAYER_HEIGHT` | 0.8, 1.6 | 타일                          |
| `INTERACT_RANGE`                | 1.0      | 타일                          |
| `MAX_HP`                        | 5        |                               |
| `WRONG_ANSWER_DAMAGE`           | 1        |                               |
| `HEALTH_POTION_HEAL`            | 2        |                               |
| `BOSS_MAX_DAMAGE`               | 100      | 가장 빨리 풀었을 때           |
| `BOSS_MIN_DAMAGE`               | 10       |                               |
| `BOSS_DAMAGE_DECAY_PER_SEC`     | 5        | 초당 감소량                   |
| `SCORE_PER_CORRECT`             | 100      |                               |
| `SCORE_STAGE_CLEAR`             | 500      |                               |
| `SAVE_SLOT_COUNT`               | 3        |                               |

## SOLID 적용

| 원칙 | 적용                                                                                                                                                                                                     |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S    | model은 데이터와 자기 규칙(보스 데미지, 아이템 효과), repository는 파일, service는 게임 규칙, screen은 그리기와 입력만 맡는다.                                                                           |
| O    | 새 문제 유형은 `Quiz` 하위 클래스와 `QuizType` 값만, 새 장애물은 `GameObject` 하위 클래스와 `ObjectType` 값만, 새 아이템은 `ItemType` 값만 추가한다. 새 화면은 `Screen` 구현과 `ScreenType` 등록만 한다. |
| L    | `Quiz`, `GameObject` 하위 클래스는 부모가 쓰이는 어디서나 동작해야 하며, 지원하지 않는 기능에서 예외를 던지지 않는다. 상호작용이 없는 `DoorObject`는 `NONE`을 돌려준다.                                  |
| I    | 저장소를 사용자·문제·스테이지·세이브로 나누고, `Screen`은 필요한 메서드만 가진다. 저장 로직은 `GameService`에서 `SaveService`로 분리한다.                                                                |
| D    | service는 repository 인터페이스에 의존하고, 구현체 연결은 `GameApp`에서만 한다.                                                                                                                          |
