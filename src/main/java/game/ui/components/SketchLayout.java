package game.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * 1280×720(16:9) 기준 좌표로 컴포넌트를 배치하는 레이아웃.
 *
 * 창 크기가 달라져도 화면 전체가 같은 배율(scale = min(가로, 세로 비율))로 커지거나 작아지므로 이미지 비율과 요소 사이
 * 간격이 유지되고, 요소끼리 겹치거나 잘리지 않는다. 남는 가로/세로 공간은 양쪽에 똑같이 나뉘어 화면 전체가 창 가운데에 놓인다
 * (그래서 문제 틀은 항상 창의 정확한 가로 중앙이다). 앵커 구분 없이 모든 요소가 이 가운데 기준을 따른다.
 */
public final class SketchLayout implements LayoutManager2 {

    public static final double DESIGN_W = 1280;
    public static final double DESIGN_H = 720;

    public enum Anchor { LEFT, CENTER, RIGHT }

    /** 시안 좌표계의 위치와 크기 */
    public record Spec(double x, double y, double w, double h, Anchor anchor) {
    }

    private final Map<Component, Spec> specs = new HashMap<>();
    private double scale = 1.0;
    private DoubleConsumer onLayout;

    public static Spec left(double x, double y, double w, double h) {
        return new Spec(x, y, w, h, Anchor.LEFT);
    }

    public static Spec center(double x, double y, double w, double h) {
        return new Spec(x, y, w, h, Anchor.CENTER);
    }

    public static Spec right(double x, double y, double w, double h) {
        return new Spec(x, y, w, h, Anchor.RIGHT);
    }

    public void setSpec(Component c, Spec spec) {
        specs.put(c, spec);
    }

    public Spec getSpec(Component c) {
        return specs.get(c);
    }

    /** 가장 최근 배치의 배율 (시안 1px = scale 화면 px) */
    public double scale() {
        return scale;
    }

    /** 배치가 끝날 때마다 배율과 함께 불린다 (글꼴 크기 갱신용). */
    public void setOnLayout(DoubleConsumer onLayout) {
        this.onLayout = onLayout;
    }

    /** 부모 크기에 맞춘 배율 */
    public static double scaleFor(int width, int height) {
        return Math.max(0.05, Math.min(width / DESIGN_W, height / DESIGN_H));
    }

    /** 시안 좌표를 부모 컨테이너의 화면 좌표로 바꾼다. */
    public Rectangle toScreen(Spec spec, int parentW, int parentH) {
        double s = scaleFor(parentW, parentH);
        double offY = (parentH - DESIGN_H * s) / 2.0;
        double x = parentW / 2.0 + (spec.x() - DESIGN_W / 2.0) * s;
        int left = (int) Math.round(x);
        int top = (int) Math.round(offY + spec.y() * s);
        int right = (int) Math.round(x + spec.w() * s);
        int bottom = (int) Math.round(offY + (spec.y() + spec.h()) * s);
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    @Override
    public void layoutContainer(Container parent) {
        int w = parent.getWidth();
        int h = parent.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        scale = scaleFor(w, h);
        if (onLayout != null) {
            onLayout.accept(scale);
        }
        for (Component c : parent.getComponents()) {
            Spec spec = specs.get(c);
            if (spec != null) {
                c.setBounds(toScreen(spec, w, h));
            }
        }
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        if (constraints instanceof Spec spec) {
            specs.put(comp, spec);
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        specs.remove(comp);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        return new Dimension((int) DESIGN_W / 2 + in.left + in.right, (int) DESIGN_H / 2 + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(320, 240);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
    }
}
