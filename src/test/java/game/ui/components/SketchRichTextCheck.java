package game.ui.components;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 문제 글 영역(SketchRichText)의 코드 구문 강조, "Q." 들여쓰기, 결과 상자를 확인한다. 화면(창) 없이 실행된다.
 * 실행: java -Dfile.encoding=UTF-8 -cp "out;src/main/resources;..." game.ui.components.SketchRichTextCheck
 */
public final class SketchRichTextCheck {

    private static int fails;
    private static int passes;

    private static void check(boolean ok, String message) {
        System.out.println((ok ? "PASS " : "FAIL ") + message);
        if (ok) {
            passes++;
        } else {
            fails++;
        }
    }

    private static String color(Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }

    /** 코드 한 줄의 구간 목록: "색:글" */
    private static List<String> segs(String code) {
        SketchRichText t = new SketchRichText();
        t.setSizes(new SketchRichText.Sizes(24, 20, 21));
        t.setParas(List.of(new SketchRichText.Para(SketchRichText.Kind.CODE, code, new Color(45, 43, 41))));
        return t.debugCodeSegments(900).get(0);
    }

    private static String colorOfToken(List<String> segs, String token) {
        for (String s : segs) {
            if (s.substring(7).equals(token)) {
                return s.substring(0, 6);
            }
        }
        return null;
    }

    public static void main(String[] args) {
        String kw = color(SketchRichText.KEYWORD_COLOR);
        String str = color(SketchRichText.STRING_COLOR);
        String num = color(SketchRichText.NUMBER_COLOR);
        String com = color(SketchRichText.COMMENT_COLOR);
        String normal = color(new Color(45, 43, 41));

        // 색 상수는 요청한 값이다
        check(kw.equals("7546A0") && str.equals("287847") && num.equals("286D9C") && com.equals("808080"),
                "구문 색: 키워드 #7546A0, 문자열 #287847, 숫자 #286D9C, 주석 #808080");

        List<String> a = segs("int x = 10; // 한글 주석");
        check(kw.equals(colorOfToken(a, "int")), "키워드 int → 보라 (" + a + ")");
        check(num.equals(colorOfToken(a, "10")), "숫자 10 → 파랑");
        check(com.equals(colorOfToken(a, "// 한글 주석")), "줄 주석(한글 포함) → 회색");
        check(normal.equals(colorOfToken(a, "x")) || a.stream().anyMatch(s -> s.startsWith(normal)), "식별자·연산자는 기본 색");

        List<String> b = segs("String s = \"if for 이동!\";");
        check(str.equals(colorOfToken(b, "\"if for 이동!\"")), "문자열 전체(안의 키워드·한글 포함) → 초록 하나의 구간 (" + b + ")");
        check(kw.equals(colorOfToken(segs("return \"a\";"), "return")), "문자열 앞의 키워드는 보라");

        List<String> c = segs("String t = \"a\\\"if\"; char q = '\\n'; int y = 0x1F;");
        check(str.equals(colorOfToken(c, "\"a\\\"if\"")), "이스케이프된 따옴표가 있는 문자열은 한 구간 (" + c + ")");
        check(str.equals(colorOfToken(c, "'\\n'")), "문자 리터럴 → 초록");
        check(num.equals(colorOfToken(c, "0x1F")), "16진수 → 파랑");

        List<String> d = segs("double z = 3.14f + a1;");
        check(num.equals(colorOfToken(d, "3.14f")), "소수 → 파랑");
        check(!num.equals(colorOfToken(d, "a1")), "식별자 안의 숫자(a1)는 숫자 색이 아니다");

        // 블록 주석은 여러 줄에 걸친다
        SketchRichText t = new SketchRichText();
        t.setSizes(new SketchRichText.Sizes(24, 20, 21));
        t.setParas(List.of(new SketchRichText.Para(SketchRichText.Kind.CODE, "/* 시작 if\n  끝 */ int k;", new Color(45, 43, 41))));
        List<List<String>> two = t.debugCodeSegments(900);
        check(two.get(0).stream().allMatch(s -> s.startsWith(com)) && two.get(1).get(0).startsWith(com)
                && two.get(1).stream().anyMatch(s -> s.startsWith(kw) && s.endsWith(":int")), "블록 주석 안의 키워드는 강조하지 않고, 주석이 끝난 뒤의 int는 보라");

        // 원본 보존: 구간을 이으면 원래 줄(들여쓰기·공백 포함)이 된다
        String src = "    for (int i = 0; i < 3; i++) {  // 반복";
        StringBuilder joined = new StringBuilder();
        for (String s : segs(src)) {
            joined.append(s.substring(7));
        }
        check(joined.toString().equals(src), "구간을 이으면 원본 줄과 같다 (들여쓰기·공백 보존)");

        // 긴 줄이 나뉘어도 글자 순서·내용이 보존되고 문자열이 두 색으로 갈라지지 않는다
        String longLine = "class B extends A { void hi() { System.out.print(\"이 문자열은 길게 이어집니다 이어집니다 이어집니다\"); } }";
        SketchRichText w = new SketchRichText();
        w.setSizes(new SketchRichText.Sizes(24, 20, 21));
        w.setParas(List.of(new SketchRichText.Para(SketchRichText.Kind.CODE, longLine, new Color(45, 43, 41))));
        List<List<String>> wrapped = w.debugCodeSegments(330);
        StringBuilder all = new StringBuilder();
        for (List<String> row : wrapped) {
            for (String s : row) {
                all.append(s.substring(7));
            }
        }
        String squeezed = all.toString().replaceAll("\\s+", "");
        check(wrapped.size() > 1 && squeezed.equals(longLine.replaceAll("\\s+", "")),
                "긴 줄은 " + wrapped.size() + "줄로 이어 쓰고 글자는 그대로 보존");
        long strSegs = wrapped.stream().flatMap(List::stream).filter(s -> s.startsWith(str)).count();
        check(strSegs >= 1 && wrapped.stream().flatMap(List::stream).filter(s -> s.startsWith(kw)).count() >= 3,
                "나뉜 줄에서도 키워드·문자열 색이 유지된다 (문자열 구간 " + strSegs + "개)");

        // 화면에 그리면 보라·초록 픽셀이 실제로 나온다 (한글 문자열 포함)
        SketchRichText r = new SketchRichText();
        r.setSizes(new SketchRichText.Sizes(24, 20, 21));
        r.setParas(List.of(new SketchRichText.Para(SketchRichText.Kind.CODE, "return \"이동!\";", new Color(45, 43, 41))));
        r.setSize(500, r.getPreferredSize().height);
        BufferedImage img = new BufferedImage(500, r.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        r.paint(g);
        g.dispose();
        int purple = 0;
        int green = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int rr = (p >> 16) & 255;
                int gg = (p >> 8) & 255;
                int bb = p & 255;
                if (Math.abs(rr - 0x75) < 40 && Math.abs(gg - 0x46) < 40 && Math.abs(bb - 0xA0) < 40) {
                    purple++;
                }
                if (Math.abs(rr - 0x28) < 40 && Math.abs(gg - 0x78) < 40 && Math.abs(bb - 0x47) < 40) {
                    green++;
                }
            }
        }
        check(purple > 20 && green > 20, "그려진 화면에 보라(" + purple + ")·초록(" + green + ") 글자가 있다");

        // "Q." 들여쓰기: 둘째 줄부터 문장 시작 위치에 맞춘다
        SketchRichText q = new SketchRichText();
        q.setSizes(new SketchRichText.Sizes(24, 20, 21));
        q.setParas(List.of(new SketchRichText.Para(SketchRichText.Kind.PROMPT,
                "A, B 인터페이스를 모두 구현하는 C 클래스의 선언으로 옳은 것은 무엇인지 고르시오 그리고 이유를 생각하시오", new Color(20, 20, 20))));
        List<String> lines = q.debugLines(536);
        int[] xs = lines.stream().skip(1).mapToInt(s -> Integer.parseInt(s.split("\\|")[0])).toArray();
        check(lines.size() >= 3 && lines.get(0).startsWith("0|") && lines.get(0).endsWith("|Q.") && xs.length >= 2 && xs[0] > 15
                        && java.util.Arrays.stream(xs).allMatch(x -> x == xs[0]),
                "Q. 뒤 문장과 둘째 줄 이후가 같은 위치(x=" + (xs.length > 0 ? xs[0] : -1) + ")에서 시작한다");
        check(q.getPlainText().startsWith("Q. A, B"), "글 전체 텍스트는 'Q. 문제'로 시작한다");

        // 결과 상자: 정답(초록 바탕)과 내가 고른 답(빨강 바탕)이 서로 다른 상자로 그려진다
        SketchRichText box = new SketchRichText();
        box.setSizes(new SketchRichText.Sizes(22, 20, 22));
        box.setParas(List.of(
                new SketchRichText.Para(SketchRichText.Kind.BOX, "정답 : 1. 객체", new Color(24, 96, 50), new Color(0xE7, 0xF1, 0xE6)),
                new SketchRichText.Para(SketchRichText.Kind.BOX, "내가 고른 답 : 2. 변수", new Color(160, 28, 28), new Color(0xFA, 0xE6, 0xE6)),
                new SketchRichText.Para(SketchRichText.Kind.BOLD, "해설", new Color(20, 20, 20)),
                new SketchRichText.Para(SketchRichText.Kind.BODY, "클래스는 설계도입니다.", new Color(20, 20, 20))));
        box.setSize(500, box.getPreferredSize().height);
        BufferedImage bi = new BufferedImage(500, box.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = bi.createGraphics();
        box.paint(bg);
        bg.dispose();
        boolean okBg = false;
        boolean badBg = false;
        for (int y = 0; y < bi.getHeight(); y++) {
            int p = bi.getRGB(490, y) & 0xFFFFFF;
            okBg |= p == 0xE7F1E6;
            badBg |= p == 0xFAE6E6;
        }
        check(okBg && badBg, "정답 상자 #E7F1E6, 내가 고른 답 상자 #FAE6E6가 그려진다");
        List<String> bl = box.debugLines(500);
        int yTitle = Integer.parseInt(bl.get(2).split("\\|")[1]);
        int yUser = Integer.parseInt(bl.get(1).split("\\|")[1]);
        check(yTitle - yUser > 22 + 6 + 8, "정답 상자와 해설 제목 사이에 여백이 있다 (" + (yTitle - yUser) + "px)");

        System.out.println("결과: PASS " + passes + " / FAIL " + fails);
        if (fails > 0) {
            System.exit(1);
        }
    }
}
