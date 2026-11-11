package main.java.model.quiz;

/**
 * 문제의 공통 부분. 유형별 판정은 하위 클래스가 isCorrect로 맡는다.
 * 호출하는 쪽은 유형을 확인하지 않고 quiz.isCorrect(input)만 쓴다.
 */
public abstract class Quiz {

    private final int id;
    private final int stageNo;
    private final String question;
    private final String explanation;

    protected Quiz(int id, int stageNo, String question, String explanation) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("문제 내용이 비어 있습니다 (문제ID " + id + ")");
        }
        this.id = id;
        this.stageNo = stageNo;
        this.question = question;
        this.explanation = explanation == null ? "" : explanation;
    }

    public abstract QuizType getType();

    /**
     * 사용자의 입력이 정답인지 판정한다.
     * 형식이 맞지 않는 입력(null, 숫자가 아닌 문자열 등)은 예외가 아니라 오답(false)이다.
     */
    public abstract boolean isCorrect(String input);

    public int getId() {
        return id;
    }

    public int getStageNo() {
        return stageNo;
    }

    public String getQuestion() {
        return question;
    }

    public String getExplanation() {
        return explanation;
    }
}
