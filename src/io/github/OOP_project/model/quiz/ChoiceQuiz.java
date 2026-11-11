package io.github.OOP_project.model.quiz;

import java.util.List;

/** 객관식. 입력은 보기 번호(1부터)이고 정답 번호와 비교한다. */
public class ChoiceQuiz extends Quiz {

    private final List<String> choices;
    private final int answerNo;   // 1부터 센다

    public ChoiceQuiz(int id, int stageNo, String question, String explanation,
                      List<String> choices, int answerNo) {
        super(id, stageNo, question, explanation);
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("보기가 없습니다 (문제ID " + id + ")");
        }
        if (answerNo < 1 || answerNo > choices.size()) {
            throw new IllegalArgumentException("정답 번호 " + answerNo + "가 보기 범위(1~" + choices.size()
                    + ")를 벗어났습니다 (문제ID " + id + ")");
        }
        this.choices = List.copyOf(choices);
        this.answerNo = answerNo;
    }

    public List<String> getChoices() {
        return choices;
    }

    public int getAnswerNo() {
        return answerNo;
    }

    @Override
    public QuizType getType() {
        return QuizType.CHOICE;
    }

    @Override
    public boolean isCorrect(String input) {
        if (input == null) {
            return false;
        }
        try {
            return Integer.parseInt(input.trim()) == answerNo;
        } catch (NumberFormatException e) {
            return false;   // 숫자가 아닌 입력은 오답으로 처리한다
        }
    }
}
