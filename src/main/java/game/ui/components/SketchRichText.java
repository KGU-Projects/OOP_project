package game.ui.components;

import game.ui.GameFonts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * 문제·코드·결과·해설을 직접 그리는 글 영역(스크롤 안에 넣어 쓴다). Swing 텍스트 컴포넌트의 기본 모양이 없다.
 *
 * 문단 종류마다 글꼴이 다르다: 문제(PROSE)·해설(BODY)은 읽기 글꼴(Pretendard), BOLD는 굵게, 코드는 고정폭 글꼴이다.
 * 코드는 옅은 회색 상자 안에 들어가고 들여쓰기·공백·줄바꿈을 그대로 지킨다. 코드가 한 줄에 안 들어가면
 * 기호 사이에서 끊어 이어 쓰되 문장부호만 단독으로 남기지 않는다. 문단 줄 간격은 글자 크기의 1.35배(코드는 1.3배)다.
 *
 * 글자 크기는 {@link Sizes}로 문단 종류별로 정하며, {@link #measure}로 주어진 폭에서의 높이를 미리 잴 수 있다.
 */
public class SketchRichText extends JComponent implements Scrollable {

    private static final long serialVersionUID = 1L;

    /**
     * PROSE: 일반 문장(Regular), PROMPT: "Q." 뒤에 이어지는 문제 문장(Q.는 크게, 둘째 줄부터는 문장 시작 위치에 맞춘 들여쓰기, Bold),
     * CODE: 코드(고정폭, 구문 강조), BODY: 해설(Regular), BOLD: 굵은 글, BOX: 색 바탕 상자 안의 굵은 글(정답·내가 고른 답)
     */
    public enum Kind { PROSE, PROMPT, CODE, BODY, BOLD, BOX }

    /** 한 문단 (줄바꿈 문자로 여러 줄을 담을 수 있다). background는 BOX일 때만 쓴다. */
    public record Para(Kind kind, String text, Color color, Color background) {
        public Para(Kind kind, String text, Color color) {
            this(kind, text, color, null);
        }
    }

    /** 문단 종류별 글자 크기(px) */
    public record Sizes(double prose, double code, double body, double spacing) {
        public Sizes(double prose, double code, double body) {
            this(prose, code, body, PROSE_SPACING);
        }
    }

    /** 측정 결과: 전체 높이와 코드 줄이 칸보다 넓어 나눠졌는지 */
    public record Measure(int height, boolean codeWrapped) {
    }

    /** 한 줄 안에서 색이 같은 구간 (코드 구문 강조) */
    private record Seg(String text, Color color) {
    }

    /**
     * 한 줄. fallback가 있으면 font에 없는 글자(한글 등)만 fallback 글꼴로 이어서 그린다 (코드 줄 안의 한글 문자열·주석).
     * segs가 있으면 구간별 색으로 이어서 그린다(코드).
     */
    private record Line(String text, Font font, Font fallback, Color color, int x, int y, List<Seg> segs) {
        Line(String text, Font font, Font fallback, Color color, int x, int y) {
            this(text, font, fallback, color, x, y, null);
        }
    }

    private record Box(Rectangle rect, Color color) {
    }

    private record Layout(List<Line> lines, List<Box> boxes, int height, boolean codeWrapped) {
    }

    // 코드 구문 강조 색
    static final Color KEYWORD_COLOR = new Color(0x75, 0x46, 0xA0);
    static final Color STRING_COLOR = new Color(0x28, 0x78, 0x47);
    static final Color NUMBER_COLOR = new Color(0x28, 0x6D, 0x9C);
    static final Color COMMENT_COLOR = new Color(0x80, 0x80, 0x80);

    private static final java.util.Set<String> KEYWORDS = java.util.Set.of("abstract", "assert", "boolean", "break",
            "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "var");

    private static final byte NORMAL = 0;
    private static final byte KEYWORD = 1;
    private static final byte STRING = 2;
    private static final byte NUMBER = 3;
    private static final byte COMMENT = 4;

    /**
     * 코드 한 줄의 글자별 종류(일반/키워드/문자열/숫자/주석)를 정한다. 문자열·주석 안의 키워드는 강조하지 않는다.
     * blockComment[0]은 /* ... *&#47; 주석이 다음 줄까지 이어지는지를 줄 사이로 넘긴다.
     */
    static byte[] classify(String s, boolean[] blockComment) {
        byte[] k = new byte[s.length()];
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (blockComment[0]) {
                int end = s.indexOf("*/", i);
                int stop = end < 0 ? s.length() : end + 2;
                java.util.Arrays.fill(k, i, stop, COMMENT);
                blockComment[0] = end < 0;
                i = stop;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                java.util.Arrays.fill(k, i, s.length(), COMMENT);
                i = s.length();
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                blockComment[0] = true;
                java.util.Arrays.fill(k, i, i + 2, COMMENT);
                i += 2;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c) {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                int stop = Math.min(s.length(), j + 1);
                java.util.Arrays.fill(k, i, stop, STRING);
                i = stop;
            } else if (Character.isDigit(c) && (i == 0 || !isIdentChar(s.charAt(i - 1)))) {
                int j = i + 1;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_'
                        || (s.charAt(j) == '.' && j + 1 < s.length() && Character.isDigit(s.charAt(j + 1))))) {
                    j++;
                }
                java.util.Arrays.fill(k, i, j, NUMBER);
                i = j;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < s.length() && Character.isJavaIdentifierPart(s.charAt(j))) {
                    j++;
                }
                if (KEYWORDS.contains(s.substring(i, j))) {
                    java.util.Arrays.fill(k, i, j, KEYWORD);
                }
                i = j;
            } else {
                i++;
            }
        }
        return k;
    }

    private static boolean isIdentChar(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    private static Color colorOf(byte kind, Color normal) {
        return switch (kind) {
            case KEYWORD -> KEYWORD_COLOR;
            case STRING -> STRING_COLOR;
            case NUMBER -> NUMBER_COLOR;
            case COMMENT -> COMMENT_COLOR;
            default -> normal;
        };
    }

    /** 원본 줄의 [start,end) 구간을 구문 색 구간으로 나눈다. pad는 앞에 붙는 들여쓰기(일반 색). */
    private static List<Seg> segments(String pad, String line, byte[] kinds, int start, int end, Color normal) {
        List<Seg> segs = new ArrayList<>();
        if (!pad.isEmpty()) {
            segs.add(new Seg(pad, normal));
        }
        int i = start;
        while (i < end) {
            int j = i + 1;
            while (j < end && kinds[j] == kinds[i]) {
                j++;
            }
            segs.add(new Seg(line.substring(i, j), colorOf(kinds[i], normal)));
            i = j;
        }
        return segs;
    }

    /** 문장 줄 간격(글자 크기의 배수). 코드는 이보다 0.08 좁다. */
    public static final double PROSE_SPACING = 1.35;
    private static final int CODE_PAD_X = 11;
    private static final int CODE_PAD_Y = 7;
    private static final Color CODE_BOX = new Color(238, 237, 233);
    private static final int BOX_PAD_X = 12;
    private static final int BOX_PAD_Y = 6;

    private List<Para> paras = List.of();
    private Sizes sizes = new Sizes(22, 17, 20);
    private Layout layout;
    private int layoutWidth = -1;

    public SketchRichText() {
        setOpaque(false);
        setFocusable(false);
    }

    public void setParas(List<Para> paras) {
        this.paras = List.copyOf(paras);
        layout = null;
        revalidate();
        repaint();
    }

    public List<Para> getParas() {
        return paras;
    }

    /** 글 전체를 줄바꿈 문자로 이은 문자열 (테스트·진단용) */
    public String getPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Para p : paras) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(p.kind() == Kind.PROMPT ? "Q. " + p.text() : p.text());
        }
        return sb.toString();
    }

    public void setSizes(Sizes sizes) {
        this.sizes = sizes;
        layout = null;
        revalidate();
        repaint();
    }

    public Sizes getSizes() {
        return sizes;
    }

    /** 코드 줄이 칸보다 넓어서 줄바꿈되었는지 (현재 글자 크기·폭 기준) */
    public boolean isCodeWrapped(int width) {
        return build(paras, width, sizes).codeWrapped();
    }

    /** 줄별 배치 정보 "x|y|글" (테스트·진단용) */
    List<String> debugLines(int width) {
        List<String> out = new ArrayList<>();
        for (Line l : build(paras, width, sizes).lines()) {
            out.add(l.x() + "|" + l.y() + "|" + l.text());
        }
        return out;
    }

    /** 코드 줄별 색 구간 "색16진수:글" 목록 (테스트·진단용) */
    List<List<String>> debugCodeSegments(int width) {
        List<List<String>> out = new ArrayList<>();
        for (Line l : build(paras, width, sizes).lines()) {
            if (l.segs() == null) {
                continue;
            }
            List<String> row = new ArrayList<>();
            for (Seg sg : l.segs()) {
                row.add(String.format("%06X", sg.color().getRGB() & 0xFFFFFF) + ":" + sg.text());
            }
            out.add(row);
        }
        return out;
    }

    /** 폭 width, 글자 크기 sizes일 때의 내용 높이와 코드 줄바꿈 여부 */
    public static Measure measure(List<Para> paras, int width, Sizes sizes) {
        Layout l = build(paras, width, sizes);
        return new Measure(l.height(), l.codeWrapped());
    }

    /** 코드 글꼴에 없는 글자(한글 등)를 그릴 글꼴: 본문 글꼴 */
    private static Font codeFallbackFont(double px) {
        return GameFonts.text(Font.PLAIN, (float) px);
    }

    /** 글꼴 primary로 그릴 수 있는 글자는 primary로, 나머지는 fallback으로 이어 그릴 때의 폭 */
    private static int mixedWidth(String text, Font primary, Font fallback, BufferedMetrics m) {
        if (primary.canDisplayUpTo(text) < 0) {
            return m.of(primary).stringWidth(text);
        }
        int w = 0;
        for (String[] run : runs(text, primary)) {
            w += m.of(run[0].equals("p") ? primary : fallback).stringWidth(run[1]);
        }
        return w;
    }

    /** 글자를 primary로 그릴 수 있는 구간("p")과 없는 구간("f")으로 나눈다 */
    private static List<String[]> runs(String text, Font primary) {
        List<String[]> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String kind = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String k = primary.canDisplay(cp) ? "p" : "f";
            if (kind != null && !k.equals(kind)) {
                out.add(new String[] {kind, cur.toString()});
                cur.setLength(0);
            }
            kind = k;
            cur.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        if (kind != null) {
            out.add(new String[] {kind, cur.toString()});
        }
        return out;
    }

    private static Font fontFor(Kind kind, double px) {
        return switch (kind) {
            case BOLD, BOX, PROMPT -> GameFonts.text(Font.BOLD, (float) px);
            default -> GameFonts.text(Font.PLAIN, (float) px);
        };
    }

    private static Layout build(List<Para> paras, int width, Sizes sizes) {
        BufferedMetrics m = new BufferedMetrics();
        List<Line> lines = new ArrayList<>();
        List<Box> boxes = new ArrayList<>();
        int y = 0;
        boolean codeWrapped = false;
        Kind previous = null;
        for (Para p : paras) {
            if (previous != null) {
                // 문단 사이 간격: 코드 상자 앞뒤는 조금 더 띄우고, 색 상자(정답) 뒤의 해설은 더 띄운다
                double gap = (p.kind() == Kind.CODE || previous == Kind.CODE) ? 0.30 : 0.26;
                if (previous == Kind.BOX) {
                    gap = p.kind() == Kind.BOX ? 0.34 : 0.62;
                }
                y += (int) Math.round(sizes.prose() * gap);
            }
            previous = p.kind();
            if (p.kind() == Kind.CODE) {
                int innerW = Math.max(1, width - CODE_PAD_X * 2);
                Font font = GameFonts.code((float) sizes.code());
                Font fb = codeFallbackFont(sizes.code());
                int lineH = (int) Math.round(sizes.code() * (sizes.spacing() - 0.08));
                int boxTop = y;
                y += CODE_PAD_Y;
                boolean[] block = {false};
                for (String raw : p.text().split("\n", -1)) {
                    String line = raw.replace("\t", "    ");
                    byte[] kinds = classify(line, block);
                    List<Piece> pieces;
                    if (mixedWidth(line, font, fb, m) <= innerW) {
                        pieces = List.of(new Piece(line, 0, line.length(), 0));
                    } else {
                        codeWrapped = true;
                        pieces = wrapCode(line, s2 -> mixedWidth(s2, font, fb, m), innerW);
                    }
                    for (Piece pc : pieces) {
                        String pad = pc.text().substring(0, pc.padLen());
                        lines.add(new Line(pc.text(), font, fb, p.color(), CODE_PAD_X, y,
                                segments(pad, line, kinds, pc.start(), pc.end(), p.color())));
                        y += lineH;
                    }
                }
                y += CODE_PAD_Y;
                boxes.add(new Box(new Rectangle(0, boxTop, width, y - boxTop), CODE_BOX));
                continue;
            }
            if (p.kind() == Kind.PROMPT) {
                // "Q."는 크게, 문장은 Q. 뒤에서 시작하고 둘째 줄부터도 같은 위치에 맞춘다 (모두 Bold, 같은 기준선)
                double qpx = sizes.prose() * 7.0 / 6.0;
                Font qf = fontFor(Kind.PROMPT, qpx);
                Font pf = fontFor(Kind.PROMPT, sizes.prose());
                FontMetrics qfm = m.of(qf);
                FontMetrics pfm = m.of(pf);
                int indent = qfm.stringWidth("Q.") + (int) Math.round(sizes.prose() * 0.5);
                int lineH = (int) Math.round(sizes.prose() * sizes.spacing());
                int dy = Math.max(0, qfm.getAscent() - pfm.getAscent());
                lines.add(new Line("Q.", qf, null, p.color(), 0, y));
                boolean first = true;
                for (String s : SketchText.wrap(p.text(), pfm, Math.max(1, width - indent))) {
                    lines.add(new Line(s, pf, null, p.color(), indent, y + dy));
                    y += first ? lineH + dy : lineH;
                    first = false;
                }
                if (first) {
                    y += lineH + dy;
                }
                continue;
            }
            if (p.kind() == Kind.BOX) {
                Font font = fontFor(Kind.BOX, sizes.body());
                FontMetrics fm = m.of(font);
                int lineH = (int) Math.round(sizes.body() * sizes.spacing());
                List<String> wrapped = SketchText.wrap(p.text(), fm, Math.max(1, width - BOX_PAD_X * 2));
                int boxTop = y;
                y += BOX_PAD_Y;
                for (String s : wrapped) {
                    lines.add(new Line(s, font, null, p.color(), BOX_PAD_X, y));
                    y += lineH;
                }
                y += BOX_PAD_Y;
                if (p.background() != null) {
                    boxes.add(new Box(new Rectangle(0, boxTop, width, y - boxTop), p.background()));
                }
                continue;
            }
            double px = p.kind() == Kind.BODY ? sizes.body() : sizes.prose();
            Font font = fontFor(p.kind(), px);
            FontMetrics fm = m.of(font);
            int lineH = (int) Math.round(px * sizes.spacing());
            for (String s : SketchText.wrap(p.text(), fm, width)) {
                lines.add(new Line(s, font, null, p.color(), 0, y));
                y += lineH;
            }
        }
        return new Layout(lines, boxes, y, codeWrapped);
    }

    /** 나눈 코드 한 줄: 화면에 그릴 글(들여쓰기 포함), 원본 줄에서의 구간, 앞에 붙인 들여쓰기 길이 */
    private record Piece(String text, int start, int end, int padLen) {
    }

    /**
     * 너무 긴 코드 줄을 나눈다. 가능하면 공백·쉼표·점·여는 괄호 뒤에서 끊고, 이어지는 줄은 원래 들여쓰기에 네 칸을 더 들여쓴다.
     * 닫는 괄호·세미콜론만 다음 줄에 단독으로 남지 않게 한다. 각 조각이 원본 줄의 어느 구간인지도 함께 돌려준다(구문 색용).
     */
    private static List<Piece> wrapCode(String line, java.util.function.ToIntFunction<String> fm, int width) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        String pad = " ".repeat(indent + 4);
        List<Piece> out = new ArrayList<>();
        int pos = 0;
        boolean firstPiece = true;
        while (pos < line.length()) {
            String prefix = firstPiece ? "" : pad;
            String rest = line.substring(pos);
            String body = firstPiece ? rest : rest.stripLeading();
            int bodyStart = pos + (rest.length() - body.length());
            if (fm.applyAsInt(prefix + body) <= width) {
                out.add(new Piece(prefix + body, bodyStart, line.length(), prefix.length()));
                break;
            }
            int cut = 1;
            while (cut < body.length() && fm.applyAsInt(prefix + body.substring(0, cut + 1)) <= width) {
                cut++;
            }
            int best = -1;
            for (int i = cut; i > cut / 2; i--) {
                char c = body.charAt(i - 1);
                if (c == ' ' || c == ',' || c == '.' || c == '(' || c == '+') {
                    best = i;
                    break;
                }
            }
            int at = best > 0 ? best : cut;
            // 남는 부분이 ) ; } 같은 부호뿐이면 앞 글자를 같이 넘긴다
            while (at > 2 && body.substring(at).strip().length() > 0 && body.substring(at).strip().length() <= 2
                    && body.substring(at).strip().chars().allMatch(ch -> ");}],.".indexOf(ch) >= 0)) {
                at--;
            }
            String piece = body.substring(0, at).stripTrailing();
            out.add(new Piece(prefix + piece, bodyStart, bodyStart + piece.length(), prefix.length()));
            pos = bodyStart + at;
            firstPiece = false;
        }
        return out;
    }

    /** 화면 그리기 없이 글자 크기를 재기 위한 도우미 */
    private static final class BufferedMetrics {
        private final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D g2 = img.createGraphics();

        BufferedMetrics() {
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        FontMetrics of(Font f) {
            return g2.getFontMetrics(f);
        }
    }

    private Layout layoutFor(int width) {
        if (layout == null || layoutWidth != width) {
            layout = build(paras, Math.max(1, width), sizes);
            layoutWidth = width;
        }
        return layout;
    }

    /** 지금 폭에서 글 전체의 높이 (스크롤이 필요한지 판단용) */
    public int contentHeight() {
        return layoutFor(getWidth() > 0 ? getWidth() : 200).height();
    }

    @Override
    public Dimension getPreferredSize() {
        int w = getWidth() > 0 ? getWidth() : 200;
        return new Dimension(w, layoutFor(w).height());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        Layout l = layoutFor(getWidth());
        Rectangle clip = g2.getClipBounds();
        for (Box box : l.boxes()) {
            Rectangle r = box.rect();
            g2.setColor(box.color());
            double arc = box.color() == CODE_BOX ? 14 : 12;
            g2.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, arc, arc));
        }
        for (Line line : l.lines()) {
            g2.setFont(line.font());
            FontMetrics fm = g2.getFontMetrics();
            int baseline = line.y() + fm.getAscent();
            if (clip != null && (baseline + fm.getDescent() < clip.y || baseline - fm.getAscent() > clip.y + clip.height)) {
                continue;
            }
            if (line.segs() != null) {
                int x = line.x();
                for (Seg seg : line.segs()) {
                    g2.setColor(seg.color());
                    x = drawMixed(g2, seg.text(), line.font(), line.fallback(), x, baseline);
                }
                continue;
            }
            g2.setColor(line.color());
            drawMixed(g2, line.text(), line.font(), line.fallback(), line.x(), baseline);
        }
        g2.dispose();
    }

    /** text를 font로 그리되 font에 없는 글자(한글 등)는 fallback으로 이어 그린다. 그린 뒤의 x를 돌려준다. */
    private static int drawMixed(Graphics2D g2, String text, Font font, Font fallback, int x, int baseline) {
        if (fallback == null || font.canDisplayUpTo(text) < 0) {
            g2.setFont(font);
            g2.drawString(text, x, baseline);
            return x + g2.getFontMetrics().stringWidth(text);
        }
        for (String[] run : runs(text, font)) {
            g2.setFont(run[0].equals("p") ? font : fallback);
            g2.drawString(run[1], x, baseline);
            x += g2.getFontMetrics().stringWidth(run[1]);
        }
        g2.setFont(font);
        return x;
    }

    // ---- Scrollable: 폭은 스크롤 영역에 맞추고, 높이만 스크롤 ----

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(12, (int) Math.round(sizes.prose() * PROSE_SPACING));
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(24, visibleRect.height - 24);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport vp && getPreferredSize().height < vp.getHeight();
    }
}
