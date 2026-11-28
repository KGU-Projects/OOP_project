package game.ui.components;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** 손그림 화면의 글자 배치 도우미: 줄바꿈과 "칸에 맞게 줄이기". */
public final class SketchText {

    private SketchText() {
    }

    /** 맞춘 결과: 쓸 글꼴과 줄들 */
    public record Fitted(Font font, List<String> lines, int lineHeight) {
    }

    /**
     * 글을 maxWidth 안에서 줄바꿈한다. 줄바꿈 문자(\n)는 그대로 지키고, 공백에서 먼저 나누며,
     * 공백 없이 너무 긴 낱말은 글자 단위로 나눈다.
     */
    public static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, fm, maxWidth, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, FontMetrics fm, int maxWidth, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ", -1)) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                int lastSpace = line.lastIndexOf(" ");
                if (lastSpace > 0 && isOnlyPunctuation(word)) {
                    // 닫는 부호(}, ), ; 등)만 다음 줄에 단독으로 남지 않게 앞 낱말과 함께 넘긴다
                    String carry = line.substring(lastSpace + 1);
                    out.add(line.substring(0, lastSpace));
                    line.setLength(0);
                    line.append(carry).append(' ').append(word);
                    continue;
                }
                out.add(line.toString());
                line.setLength(0);
            }
            // 낱말 하나가 한 줄보다 길면 나눈다: 가능하면 . ( , 뒤에서 끊고, 괄호·세미콜론만 다음 줄에 남지 않게 한다
            String rest = word;
            while (fm.stringWidth(rest) > maxWidth && rest.length() > 1) {
                int cut = 1;
                while (cut < rest.length() && fm.stringWidth(rest.substring(0, cut + 1)) <= maxWidth) {
                    cut++;
                }
                cut = niceCut(rest, cut);
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        out.add(line.toString());
    }

    /** 한 줄에 cut글자까지 들어갈 때 실제로 끊을 위치. 뒤쪽 절반 안에서 . ( , 뒤를 찾고, 남는 조각이 문장부호뿐이면 앞에서 더 가져온다. */
    private static int niceCut(String rest, int cut) {
        if (cut >= rest.length()) {
            return cut;
        }
        int best = -1;
        for (int i = cut; i > cut / 2; i--) {
            char c = rest.charAt(i - 1);
            if (c == '.' || c == '(' || c == ',' || c == '+' || c == '=') {
                best = i;
                break;
            }
        }
        int result = best > 0 ? best : cut;
        // 남는 조각이 ) ] } ; , . 같은 문장부호뿐(최대 3글자)이면 한 줄에 단독으로 남지 않게 앞 글자를 같이 넘긴다
        while (result > 2 && isOnlyPunctuation(rest.substring(result)) ) {
            result--;
            if (rest.length() - result >= 4) {
                break;
            }
        }
        return Math.max(1, result);
    }

    private static boolean isOnlyPunctuation(String s) {
        if (s.isEmpty() || s.length() > 3) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (")]};,.>\"'".indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * w×h 칸에 들어가도록 글꼴을 조금씩 줄여 가며 줄바꿈한다. minScale까지 줄여도 안 들어가면
     * 마지막 줄 끝을 …으로 줄인다. 글자가 칸을 벗어나지 않는다.
     */
    static Fitted fit(Graphics2D g2, String text, Font base, int w, int h, double minScale) {
        // 먼저 한 줄에 들어가도록 조금(최대 28%) 줄여 본다: 두 줄로 어색하게 끊기는 것보다 한 줄이 읽기 좋다.
        if (text.indexOf('\n') < 0) {
            for (double scale = 1.0; scale >= Math.max(minScale, 0.72) - 1e-6; scale -= 0.04) {
                Font font = base.deriveFont((float) Math.max(6.0, base.getSize2D() * scale));
                FontMetrics fm = g2.getFontMetrics(font);
                if (fm.stringWidth(text) <= w && fm.getHeight() <= h) {
                    return new Fitted(font, List.of(text), fm.getHeight());
                }
            }
        }
        Fitted last = null;
        for (double scale = 1.0; scale >= minScale - 1e-6; scale -= 0.05) {
            Font font = base.deriveFont((float) Math.max(6.0, base.getSize2D() * scale));
            FontMetrics fm = g2.getFontMetrics(font);
            List<String> lines = wrap(text, fm, w);
            int lineHeight = fm.getHeight();
            last = new Fitted(font, lines, lineHeight);
            if (lines.size() * lineHeight <= h && widest(lines, fm) <= w) {
                return last;
            }
        }
        // 그래도 넘치면 들어가는 줄만 남기고 마지막 줄을 줄인다
        FontMetrics fm = g2.getFontMetrics(last.font());
        int maxLines = Math.max(1, h / Math.max(1, last.lineHeight()));
        List<String> lines = new ArrayList<>(last.lines());
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, ellipsize(lines.get(maxLines - 1) + "…", fm, w));
        } else {
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, ellipsize(lines.get(i), fm, w));
            }
        }
        return new Fitted(last.font(), lines, last.lineHeight());
    }

    private static int widest(List<String> lines, FontMetrics fm) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, fm.stringWidth(line));
        }
        return max;
    }

    private static String ellipsize(String s, FontMetrics fm, int w) {
        if (fm.stringWidth(s) <= w) {
            return s;
        }
        String cut = s.endsWith("…") ? s.substring(0, s.length() - 1) : s;
        while (cut.length() > 1 && fm.stringWidth(cut + "…") > w) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
