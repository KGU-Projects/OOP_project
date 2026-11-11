package main.java.game.model.quiz;

/** 입력형. 앞뒤 공백을 제거하고 대소문자를 무시해 정답 문자열과 비교한다. */
public class InputQuiz extends Quiz {

    private final String answer;

    public InputQuiz(int id, int stageNo, String question, String explanation, String answer) {
        super(id, stageNo, question, explanation);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("정답이 비어 있습니다 (문제ID " + id + ")");
        }
        this.answer = answer.trim();
    }

    public String getAnswer() {
        return answer;
    }

    @Override
    public QuizType getType() {
        return QuizType.INPUT;
    }

    @Override
    public boolean isCorrect(String input) {
        return input != null && answer.equalsIgnoreCase(input.trim());
    }
}
