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
import java.util.Optional;

/**
 * QuizScreen 단독 실행·테스트용 데모.
 *
 * 화면 흐름은 실제 게임과 같은 {@link QuizBattleController}가 맡고, 이 클래스는 그 컨트롤러에 넣는
 * 데모용 전투 규칙({@link QuizBattleController.BattleRules})만 구현한다. 실제 게임에서는 이 자리에
 * GameService를 감싼 규칙이 들어간다.
 *
 * 데모 규칙: 플레이어 HP 3, 몬스터 HP 3, 문제당 20초. 정답이면 몬스터 HP -1, 오답·시간 초과면 플레이어 HP -1.
 * HP가 1이 되면 경고 팝업, 일시정지 버튼, HP 0이면 GAME OVER 팝업이 뜬다.
 * 데모에는 맵·스테이지가 없으므로 화면을 떠나는 선택(승리 후 '다시 플레이', 전투 중단, 체력 보충, 재시작,
 * 스테이지 선택)은 모두 즉시 재도전으로 처리한다 (HP를 처음 값으로 되돌린다).
 *
 * 실행: java -cp out game.ui.screens.QuizScreenDemo [제한시간초]
 */
public final class QuizScreenDemo implements QuizBattleController.BattleRules {

    public static final int PLAYER_MAX_HP = 3;
    public static final int MONSTER_MAX_HP = 3;
    public static final int TIME_LIMIT_SECONDS = 20;
    private static final String RETURN_BUTTON_TEXT = "다시 플레이";

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

    public enum BattleState {
        READY, IN_PROGRESS, VICTORY, DEFEAT
    }

    private final List<Quiz> quizzes;
    private final QuizBattleController controller;

    private int playerHp;
    private int monsterHp;
    private int nextQuizIndex;
    private boolean started;
    private QuizBattleController.ExitReason lastExitReason;

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
        this.quizzes = List.copyOf(quizzes);
        // 데모는 어떤 이유로 화면을 떠나든 바로 다시 시작한다
        this.controller = new QuizBattleController(screen, this, timeLimitSeconds, RETURN_BUTTON_TEXT,
                reason -> {
                    lastExitReason = reason;
                    startBattle();
                });
    }

    /** HP와 문제 순서를 처음으로 되돌리고 전투를 시작한다. */
    public void startBattle() {
        playerHp = PLAYER_MAX_HP;
        monsterHp = MONSTER_MAX_HP;
        nextQuizIndex = 0;
        started = true;
        controller.start("사용자", "몬스터 (오류 버그)");
    }

    // ---------------------------------------------------------------------
    // QuizBattleController.BattleRules (데모 규칙)
    // ---------------------------------------------------------------------

    /** 목록을 순서대로 돌려 쓴다. 끝나면 처음부터 다시 내므로 문제가 모자라 멈추지 않는다. */
    @Override
    public Optional<Quiz> nextQuiz() {
        Quiz quiz = quizzes.get(nextQuizIndex % quizzes.size());
        nextQuizIndex++;
        return Optional.of(quiz);
    }

    @Override
    public QuizBattleController.Judgement submitAnswer(Quiz quiz, String answer) {
        if (quiz.isCorrect(answer)) {
            monsterHp = Math.max(0, monsterHp - 1);
            return new QuizBattleController.Judgement(true, 0, 1, false, monsterHp == 0);
        }
        return damagePlayer();
    }

    @Override
    public QuizBattleController.Judgement timeOut(Quiz quiz) {
        return damagePlayer();
    }

    @Override
    public QuizBattleController.HpView hp() {
        return new QuizBattleController.HpView(playerHp, PLAYER_MAX_HP, monsterHp, MONSTER_MAX_HP);
    }

    private QuizBattleController.Judgement damagePlayer() {
        playerHp = Math.max(0, playerHp - 1);
        return new QuizBattleController.Judgement(false, 1, 0, playerHp == 0, false);
    }

    // ---------------------------------------------------------------------
    // 상태 조회 (테스트용)
    // ---------------------------------------------------------------------

    public BattleState getBattleState() {
        if (!started) {
            return BattleState.READY;
        }
        QuizBattleController.Outcome outcome = controller.getOutcome();
        if (outcome == QuizBattleController.Outcome.VICTORY) {
            return BattleState.VICTORY;
        }
        if (outcome == QuizBattleController.Outcome.DEFEAT) {
            return BattleState.DEFEAT;
        }
        if (outcome != null) {
            return BattleState.READY; // 중단·후퇴·문제 없음: 곧 재시작된다
        }
        return BattleState.IN_PROGRESS;
    }

    public int getPlayerHp() {
        return playerHp;
    }

    public int getMonsterHp() {
        return monsterHp;
    }

    public Quiz getCurrentQuiz() {
        return controller.getCurrentQuiz();
    }

    /** 마지막으로 화면을 떠난 이유 (아직 없으면 null) */
    public QuizBattleController.ExitReason getLastExitReason() {
        return lastExitReason;
    }

    public int getQuestionCount() {
        return controller.getQuestionCount();
    }

    /** 화면 이벤트를 받는 컨트롤러 (테스트에서 중복 이벤트를 직접 보낼 때 사용) */
    public QuizBattleController getController() {
        return controller;
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
                new ChoiceQuiz(908, 1,
                        "다음 설명을 모두 만족하는 객체지향 개념을 고르시오.\n"
                                + "- 클래스의 필드를 private으로 숨긴다.\n"
                                + "- 외부에서는 getter와 setter 같은 공개 메서드로만 값을 다룬다.\n"
                                + "- 잘못된 값이 들어오지 않도록 메서드 안에서 검사할 수 있다.",
                        "데이터와 그 데이터를 다루는 메서드를 하나로 묶고, 내부 구현을 감추는 것을 캡슐화라고 합니다.",
                        List.of("상속", "캡슐화", "다형성", "추상화"),
                        2),
                new InputQuiz(904, 1,
                        "EBook이 Book을 상속하도록 빈칸에 들어갈 키워드를 쓰시오.\n"
                                + "class Book { }\n"
                                + "class EBook ______ Book { }",
                        "클래스를 상속할 때는 class 서브클래스 extends 슈퍼클래스 형태로 씁니다.",
                        "extends"),
                new InputQuiz(909, 1,
                        "은행 계좌 클래스 Account가 있다. 생성자는 처음 잔액을 받고, deposit()은 입금, withdraw()는 출금을 하며, "
                                + "잔액보다 큰 금액을 출금하려 하면 출금하지 않고 false를 돌려준다. 출금에 성공하면 true를 돌려준다. "
                                + "main()에서 여러 번 입금·출금을 한 뒤 마지막 잔액을 출력할 때, 출력되는 값을 숫자로 쓰시오. "
                                + "(출금 실패는 잔액에 영향을 주지 않는다.)\n"
                                + "class Account {\n"
                                + "    private int balance;\n"
                                + "\n"
                                + "    Account(int balance) {\n"
                                + "        this.balance = balance;\n"
                                + "    }\n"
                                + "\n"
                                + "    void deposit(int amount) {\n"
                                + "        balance += amount;\n"
                                + "    }\n"
                                + "\n"
                                + "    boolean withdraw(int amount) {\n"
                                + "        if (amount > balance) {\n"
                                + "            return false;\n"
                                + "        }\n"
                                + "        balance -= amount;\n"
                                + "        return true;\n"
                                + "    }\n"
                                + "\n"
                                + "    int getBalance() {\n"
                                + "        return balance;\n"
                                + "    }\n"
                                + "}\n"
                                + "\n"
                                + "public class Main {\n"
                                + "    public static void main(String[] args) {\n"
                                + "        Account acc = new Account(1000);\n"
                                + "        acc.deposit(500);          // 1500\n"
                                + "        acc.withdraw(2000);        // 실패\n"
                                + "        acc.withdraw(700);         // 800\n"
                                + "        acc.deposit(200);\n"
                                + "        System.out.println(acc.getBalance());\n"
                                + "    }\n"
                                + "}\n"
                                + "위 코드의 출력 결과는?",
                        "처음 잔액 1000원에 500원을 입금하면 1500원이 됩니다. 2000원 출금은 잔액보다 커서 withdraw()가 false를 돌려주고 "
                                + "잔액은 그대로 1500원입니다. 이어서 700원을 출금하면 800원, 마지막으로 200원을 입금하면 1000원이 됩니다. "
                                + "balance 필드는 private이라 main()에서 직접 바꿀 수 없고, 반드시 deposit()과 withdraw()를 거쳐야 합니다. "
                                + "이렇게 메서드 안에서 잘못된 출금을 막는 것이 캡슐화의 대표적인 장점입니다. "
                                + "또한 withdraw()의 반환값을 확인하지 않으면 출금 실패를 알아차리지 못하므로, 실제 프로그램에서는 "
                                + "if (!acc.withdraw(2000)) { ... }처럼 결과를 검사해 사용자에게 알려 주는 것이 좋습니다.",
                        "1000"),
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
