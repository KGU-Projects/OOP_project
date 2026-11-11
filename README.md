# CODE ESCAPE

Java Swing 기반 2D 학습형 퍼즐 플랫포머.
옆에서 보는 맵을 달리고 점프하며, 버튼의 CS·자바 퀴즈를 풀어 막힌 벽을 열고, 열쇠를 모아 보스를 쓰러뜨리는 게임이다.

## 게임 소개

- 플레이어는 한 명이다. 이동과 점프로 맵을 탐험한다.
- 맵의 버튼과 상호작용하면 퀴즈가 나온다. 맞히면 연결된 벽(문)이 열리고, 틀리면 체력이 깎인다. 맵의 함정이나 적으로는 피해를 입지 않는다.
- 스테이지마다 열쇠가 하나 있고, 얻으면 스테이지가 클리어된다. 열쇠는 인벤토리에 보관된다. 중간에 체력 포션 같은 아이템이 있고, 인벤토리에서 사용할 수 있다.
- 모든 열쇠를 모으면 보스 스테이지에 입장한다. 보스는 퀴즈를 풀어야 데미지를 줄 수 있고, 빨리 풀수록 데미지가 크다.
- 체력이 0이 되면 "처음부터 시작"(현재 스테이지 처음)과 "마지막 저장 위치에서 시작" 중에서 고른다.
- 사용자마다 세이브 슬롯을 여러 개 가진다.

### 조작

| 키           | 동작                                |
| ------------ | ----------------------------------- |
| A, D (←, →)  | 좌우 이동                           |
| W, Space (↑) | 점프                                |
| E            | 버튼, 열쇠, 아이템, 보스와 상호작용 |
| 1 ~ 9        | 인벤토리 아이템 사용                |
| ESC          | 정지 화면 (계속하기, 저장, 나가기)  |

## 실행 방법

JDK 27(OpenJDK 27)을 사용한다. `GameApp`이 인스턴스 `main()`을 쓰기 때문에 JDK 25 이상이어야 컴파일된다.

```bash
# 프로젝트 루트에서
make                  # 컴파일 (out/ 에 .class 생성)
java -cp out io.github.OOP_project.GameApp    # 게임 실행
```

| 명령                   | 하는 일                                                |
| ---------------------- | ------------------------------------------------------ |
| `make`                 | 소스가 바뀐 경우에만 컴파일한다 (`all`)                |
| `make preview`         | 모든 스테이지 미리보기. `make preview STAGE=3`은 3스테이지만 |
| `make clean`           | 컴파일된 `.class`를 지운다                             |
| `make fclean`          | `out/` 폴더까지 지운다                                 |
| `make re`              | `fclean` 후 다시 컴파일한다                            |

`make`가 없으면 직접 컴파일한다.

```bash
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
```

Swing 창이 필요하므로 GUI 환경에서 실행한다. (WSL2는 WSLg 필요)

### 스테이지 미리보기

스테이지 파일의 지형과 오브젝트를 겹쳐 글자 그림으로 보여 주는 개발용 도구다. 형식이 틀리면 파일 이름과 줄 번호를 알려 준다. 프로젝트 루트에서 실행한다.

```bash
make preview STAGE=1    # 1스테이지
make preview            # 모든 스테이지
```

## 프로젝트 구조

기본 패키지는 `io.github.OOP_project`이다.

```
src/io/github/OOP_project/
  GameApp       main, 객체 조립
  common/       ScreenType, GameConstants
  model/        User, SaveData, Stage, Player, GameState ...
    object/     GameObject, ButtonObject, DoorObject, KeyObject, ItemObject, BossObject
    quiz/       Quiz, ChoiceQuiz, InputQuiz
  repository/   저장소 인터페이스
    file/       텍스트 파일 구현체
  service/      UserService, GameService, SaveService, PhysicsEngine
  tool/         StagePreview (개발용 맵 미리보기)
  ui/           Screen, ScreenManager
    screens/    화면
    components/ 재사용 컴포넌트
data/           users.txt, quiz.txt, stages/, saves/
docs/           기획·설계 문서
```

의존 방향은 `ui → service → repository 인터페이스 ← repository/file`이고, 모든 계층이 `model`과 `common`을 쓴다. 구현체 연결은 `GameApp`에서만 한다.

## 설계 특징

- **확장 가능한 오브젝트**: 버튼, 문, 열쇠, 아이템, 보스는 `GameObject`의 하위 클래스다. 새 장애물은 클래스 하나와 종류 이름 하나만 추가한다.
- **퀴즈 유형 다형성**: 객관식과 입력형을 `quiz.isCorrect(input)`으로 판정하며, 유형별 분기를 하지 않는다.
- **텍스트 파일 데이터**: 문제, 스테이지, 사용자, 세이브를 `|` 구분 텍스트 파일로 관리한다. 문제와 스테이지는 파일을 직접 편집해서 추가·수정한다.
- **게임 루프**: `javax.swing.Timer`와 Key Bindings를 쓰고, 이동·충돌 계산은 Swing과 분리된 `PhysicsEngine`이 맡는다.
- **외부 라이브러리 없음**: Java 표준 라이브러리만 사용한다.

## 데이터 파일

| 파일                            | 내용                                              |
| ------------------------------- | ------------------------------------------------- |
| `data/users.txt`                | 닉네임, 최고점수, 클리어 수, 마지막 플레이 일시   |
| `data/quiz.txt`                 | 문제 (객관식·입력형), 보스 문제 풀 포함           |
| `data/stages/stageN.txt`        | 스테이지 하나: 글자 격자 지형 + 오브젝트 목록     |
| `data/saves/<닉네임>/slotN.txt` | 저장 슬롯 하나 (위치, 체력, 인벤토리, 열린 문 등) |

버튼과 퀴즈의 연결은 스테이지 파일의 `BUTTON` 줄에 문제ID를 적어서 한다. 자세한 형식은 `docs/data-format.md`를 본다.

## 문서

| 문서                    | 내용                                                   |
| ----------------------- | ------------------------------------------------------ |
| `docs/project.md`       | 기획, 게임 규칙, 화면 흐름, 일정, 역할 분담            |
| `docs/architecture.md`  | 패키지 구조, 클래스 설계, 인터페이스, 상수, SOLID 적용 |
| `docs/class-diagram.md` | 계층별 클래스 다이어그램                               |
| `docs/data-format.md`   | 데이터 파일 형식                                       |
| `docs/collaboration.md` | 팀 규칙, Git 규칙                                      |

## 개발 상태

시작 화면(`StartScreen`), 화면 전환(`ScreenManager`), 임시 `UserService`, 스테이지 파일 읽기(`FileStageRepository`)와 맵 미리보기 도구까지 구현되어 있다. 나머지는 `docs/project.md`의 일정과 역할 분담에 따라 개발한다.
