package game.ui.screens;

import game.common.ScreenType;
import game.model.quiz.ChoiceQuiz;
import game.model.quiz.InputQuiz;
import game.model.quiz.Quiz;
import game.ui.ScreenManager;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.List;

/**
 * QuizScreen 단독 실행·테스트용 전투 진행기.
 *
 * 데모용 문제 목록, 플레이어·몬스터 HP, 정답 판정(quiz.isCorrect), 승패 판정을 맡는다.
 * 화면은 QuizScreen.QuizListener로만 연결되므로, 실제 게임에서는 이 클래스 대신
 * 게임 로직(GameService 등)이 같은 리스너를 구현하면 된다.
 *
 * 실행: 프로젝트 루트에서 컴파일 후 java -cp out game.ui.screens.QuizScreenDemo [제한시간초]
 */
public final class QuizScreenDemo implements QuizScreen.QuizListener {

    public static final int PLAYER_MAX_HP = 5;
    public static final int MONSTER_MAX_HP = 3;
    public static final int TIME_LIMIT_SECONDS = 20;

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    public enum BattleState {
        READY, IN_PROGRESS, VICTORY, DEFEAT
    }

    private final QuizScreen screen;
    private final List<Quiz> quizzes;
    private final int timeLimitSeconds;

    private BattleState battleState = BattleState.READY;
    private int playerHp;
    private int monsterHp;
    private int nextQuizIndex;
    private int questionCount;
    private Quiz currentQuiz;
    private boolean awaitingAnswer;

    public QuizScreenDemo(QuizScreen screen, List<Quiz> quizzes, int timeLimitSeconds) {
        if (screen == null) {
            throw new IllegalArgumentException("screen is null");
        }
        if (quizzes == null || quizzes.isEmpty()) {
            throw new IllegalArgumentException("문제가 하나 이상 필요합니다");
        }
        if (timeLimitSeconds <= 0) {
            throw new IllegalArgumentException("timeLimitSeconds must be positive: " + timeLimitSeconds);
        }
        this.screen = screen;
        this.quizzes = List.copyOf(quizzes);
        this.timeLimitSeconds = timeLimitSeconds;
        screen.setQuizListener(this);
    }

    /** HP를 초기화하고 첫 문제를 낸다. 전투 중에 다시 부르면 처음부터 다시 시작한다. */
    public void startBattle() {
        playerHp = PLAYER_MAX_HP;
        monsterHp = MONSTER_MAX_HP;
        nextQuizIndex = 0;
        questionCount = 0;
        battleState = BattleState.IN_PROGRESS;

        screen.resetBattle();
        screen.setCombatantNames("사용자", "몬스터 (오류 버그)");
        screen.updateHp(playerHp, PLAYER_MAX_HP, monsterHp, MONSTER_MAX_HP);
        showNextQuiz();
    }

    // ---------------------------------------------------------------------
    // QuizScreen.QuizListener
    // ---------------------------------------------------------------------

    @Override
    public void onAnswerSubmitted(Quiz quiz, String answer) {
        if (!acceptsResultFor(quiz)) {
            return;
        }
        boolean correct = quiz.isCorrect(answer);
        applyResult(correct ? QuizScreen.ResultType.CORRECT : QuizScreen.ResultType.WRONG);
    }

    @Override
    public void onTimeExpired(Quiz quiz) {
        if (!acceptsResultFor(quiz)) {
            return;
        }
        applyResult(QuizScreen.ResultType.TIMEOUT);
    }

    @Override
    public void onNextRequested() {
        if (battleState != BattleState.IN_PROGRESS || awaitingAnswer) {
            return;
        }
        showNextQuiz();
    }

    // ---------------------------------------------------------------------
    // 전투 규칙
    // ---------------------------------------------------------------------

    /** 진행 중인 전투의 현재 문제에 대해 아직 판정하지 않았을 때만 결과를 받는다 (중복 판정 방지). */
    private boolean acceptsResultFor(Quiz quiz) {
        return battleState == BattleState.IN_PROGRESS && awaitingAnswer && quiz == currentQuiz;
    }

    private void applyResult(QuizScreen.ResultType type) {
        awaitingAnswer = false;

        String detail;
        if (type == QuizScreen.ResultType.CORRECT) {
            monsterHp = Math.max(0, monsterHp - 1);
            detail = "반격 성공! 몬스터 HP -1";
        } else if (type == QuizScreen.ResultType.WRONG) {
            playerHp = Math.max(0, playerHp - 1);
            detail = "방어 실패! 사용자 HP -1";
        } else {
            playerHp = Math.max(0, playerHp - 1);
            detail = "제한시간 " + timeLimitSeconds + "초가 지났습니다. 사용자 HP -1";
        }
        screen.updateHp(playerHp, PLAYER_MAX_HP, monsterHp, MONSTER_MAX_HP);

        if (monsterHp <= 0) {
            battleState = BattleState.VICTORY;
        } else if (playerHp <= 0) {
            battleState = BattleState.DEFEAT;
        }

        boolean battleOver = battleState != BattleState.IN_PROGRESS;
        screen.showResult(type, detail, !battleOver);
        if (battleState == BattleState.VICTORY) {
            screen.showBattleEnd(true, "몬스터를 쓰러뜨렸습니다.");
        } else if (battleState == BattleState.DEFEAT) {
            screen.showBattleEnd(false, "체력이 모두 소진되었습니다.");
        }
    }

    /** 문제는 목록을 순서대로 돌려 쓴다. 목록이 끝나면 처음부터 다시 내므로 문제가 모자라 멈추지 않는다. */
    private void showNextQuiz() {
        currentQuiz = quizzes.get(nextQuizIndex % quizzes.size());
        nextQuizIndex++;
        questionCount++;
        awaitingAnswer = true;
        screen.showQuiz(currentQuiz, questionCount, timeLimitSeconds);
    }

    public BattleState getBattleState() {
        return battleState;
    }

    public int getPlayerHp() {
        return playerHp;
    }

    public int getMonsterHp() {
        return monsterHp;
    }

    public Quiz getCurrentQuiz() {
        return currentQuiz;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    // ---------------------------------------------------------------------
    // 데모용 문제 (기존 ChoiceQuiz / InputQuiz 모델로 생성)
    // ---------------------------------------------------------------------

    public static List<Quiz> createDemoQuizzes() {
        return List.of(
                new ChoiceQuiz(901, 1,
                        "다음 중 오버라이딩에 대한 설명으로 옳은 것은?",
                        "오버라이딩은 상속받은 부모 클래스의 메서드를 자식 클래스에서 같은 시그니처로 다시 정의하는 것입니다. "
                                + "같은 이름의 메서드를 매개변수만 다르게 여러 개 만드는 것은 오버로딩입니다.",
                        List.of("같은 이름의 메서드를 여러 개 정의한다.",
                                "부모 클래스의 메서드를 재정의한다.",
                                "static 메서드를 재정의한다.",
                                "생성자를 재정의한다."),
                        2),
                new InputQuiz(902, 1,
                        "다음 코드의 출력 결과를 쓰시오.\n"
                                + "String aLine = \"the happiest day\";\n"
                                + "String mood = aLine.substring(4, 12);\n"
                                + "System.out.println(mood);",
                        "substring(시작, 끝)은 시작 인덱스는 포함하고 끝 인덱스는 포함하지 않습니다. 4~11번 글자인 happiest가 출력됩니다.",
                        "happiest"),
                new ChoiceQuiz(903, 1,
                        "다음 코드를 실행했을 때 마지막 줄에 출력되는 값은?\n"
                                + "class Animal {\n"
                                + "    String name = \"동물\";\n"
                                + "\n"
                                + "    void speak() {\n"
                                + "        System.out.println(\"...\");\n"
                                + "    }\n"
                                + "}\n"
                                + "\n"
                                + "class Dog extends Animal {\n"
                                + "    @Override\n"
                                + "    void speak() {\n"
                                + "        System.out.println(\"멍멍\");\n"
                                + "    }\n"
                                + "}\n"
                                + "\n"
                                + "class Cat extends Animal {\n"
                                + "    @Override\n"
                                + "    void speak() {\n"
                                + "        System.out.println(\"야옹\");\n"
                                + "    }\n"
                                + "}\n"
                                + "\n"
                                + "public class Main {\n"
                                + "    public static void main(String[] args) {\n"
                                + "        Animal[] animals = { new Dog(), new Cat(), new Animal(), new Dog() };\n"
                                + "        int count = 0;\n"
                                + "        for (Animal a : animals) {\n"
                                + "            if (a instanceof Dog) {\n"
                                + "                count++;\n"
                                + "            }\n"
                                + "            a.speak();\n"
                                + "        }\n"
                                + "        System.out.println(count);\n"
                                + "    }\n"
                                + "}",
                        "배열에 Dog 객체가 두 개 있으므로 instanceof Dog가 두 번 참이 되어 count는 2입니다. "
                                + "speak()는 실제 객체의 오버라이딩된 메서드가 실행됩니다(다형성).",
                        List.of("0", "1", "2", "4"),
                        3),
                new InputQuiz(904, 1,
                        "EBook이 Book을 상속하도록 빈칸에 들어갈 키워드를 쓰시오.\n"
                                + "class Book { }\n"
                                + "class EBook ______ Book { }",
                        "클래스를 상속할 때는 class 서브클래스 extends 슈퍼클래스 형태로 씁니다.",
                        "extends"),
                new ChoiceQuiz(905, 1,
                        "ArrayList에 저장된 요소의 개수를 반환하는 메서드는?",
                        "ArrayList의 size()는 현재 저장된 요소 개수를 반환합니다. 배열의 길이는 length 필드로 확인합니다.",
                        List.of("length()", "size()", "count()", "capacity()"),
                        2),
                new InputQuiz(906, 1,
                        "다음 코드의 출력 결과를 쓰시오.\n"
                                + "int sum = 0;\n"
                                + "for (int i = 1; i <= 10; i++) {\n"
                                + "\tif (i % 3 == 0) {\n"
                                + "\t\tcontinue;\n"
                                + "\t}\n"
                                + "\tsum += i;\n"
                                + "}\n"
                                + "System.out.println(sum);",
                        "1~10의 합 55에서 3의 배수 3, 6, 9(합 18)를 건너뛰므로 37이 출력됩니다.",
                        "37"),
                new ChoiceQuiz(907, 1,
                        "다음 코드 실행 후 array[2]의 값은?\n"
                                + "int[] array = new int[5];\n"
                                + "int[] newArray = array;\n"
                                + "newArray[2] = 10;",
                        "배열 대입은 복사가 아니라 같은 배열을 가리키게 합니다. 그래서 array[2]도 10이 됩니다.",
                        List.of("0", "10", "5", "컴파일 오류"),
                        2));
    }

    // ---------------------------------------------------------------------
    // 독립 실행
    // ---------------------------------------------------------------------

    /** 인자로 제한시간(초)을 줄 수 있다. 없으면 20초. */
    public static void main(String[] args) {
        int timeLimit = TIME_LIMIT_SECONDS;
        if (args.length > 0) {
            try {
                timeLimit = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("제한시간은 숫자로 입력하세요. 기본값 " + TIME_LIMIT_SECONDS + "초를 사용합니다.");
            }
            if (timeLimit <= 0) {
                timeLimit = TIME_LIMIT_SECONDS;
            }
        }
        int finalTimeLimit = timeLimit;
        SwingUtilities.invokeLater(() -> launch(finalTimeLimit));
    }

    /** 데모 창을 띄우고 전투를 시작한다. EDT에서 호출한다. */
    public static QuizScreenDemo launch(int timeLimitSeconds) {
        JFrame frame = new JFrame("CODE ESCAPE - 퀴즈 전투 데모");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        // 실제 게임과 같은 방식(CardLayout + ScreenManager)으로 화면을 등록한다
        CardLayout cardLayout = new CardLayout();
        JPanel container = new JPanel(cardLayout);
        ScreenManager screenManager = new ScreenManager(container, cardLayout);

        QuizScreen quizScreen = new QuizScreen(screenManager);
        screenManager.register(ScreenType.QUIZ, quizScreen);
        QuizScreenDemo demo = new QuizScreenDemo(quizScreen, createDemoQuizzes(), timeLimitSeconds);

        frame.add(container);
        frame.setVisible(true);
        screenManager.show(ScreenType.QUIZ);
        demo.startBattle();
        return demo;
    }
}
