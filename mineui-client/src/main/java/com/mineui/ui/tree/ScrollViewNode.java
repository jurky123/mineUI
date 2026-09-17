package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;

import java.util.List;

/** 可滚动容器：高度固定为视口，内容超出后可滚动（滚轮 / 代码）。 */
public final class ScrollViewNode extends ContainerNode {

    private static final float WHEEL_STEP = 24f;

    private float scrollOffset;
    private float contentHeight;

    public ScrollViewNode(NodeStyle style) {
        super(style);
    }

    public float scrollOffset() {
        return scrollOffset;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public boolean scrollable() {
        return contentHeight > height + 0.5f;
    }

    /** 程序化滚动（会被钳制到合法范围）。 */
    public void scrollTo(float offset) {
        scrollOffset = clamp(offset);
        layout(x, y);
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            contentHeight = 0;
            return;
        }
        Insets pad = style().padding();
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float contentW = contentWidth(resolvedW, context.availableWidth(), pad);
        float viewportH = contentHeight(resolvedH, context.availableHeight(), pad);

        List<UiNode> kids = children();
        float used = 0;
        float maxCross = 0;
        int visibleCount = 0;
        for (UiNode child : kids) {
            child.measure(context.withAvailable(contentW, viewportH));
            if (!child.visibleNow()) {
                continue;
            }
            used += child.height();
            maxCross = Math.max(maxCross, child.width());
            visibleCount++;
        }
        if (visibleCount > 1) {
            used += gap() * (visibleCount - 1);
        }
        contentHeight = pad.vertical() + used;

        width = resolvedW >= 0 ? resolvedW : pad.horizontal() + maxCross;
        height = resolvedH >= 0 ? resolvedH : contentHeight;
        scrollOffset = clamp(scrollOffset);
    }

    @Override
    public void layout(float x, float y) {
        super.layout(x, y);
        Insets pad = style().padding();
        float cursor = y + pad.top() - scrollOffset;
        for (UiNode child : children()) {
            if (!child.visibleNow()) {
                continue;
            }
            child.layout(x + pad.left(), cursor);
            cursor += child.height() + gap();
        }
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (!visibleNow() || !contains(mx, my)) {
            return false;
        }
        for (UiNode child : childrenByZDesc()) {
            if (child.scroll(mx, my, amount)) {
                return true;
            }
        }
        if (!scrollable()) {
            return false;
        }
        float before = scrollOffset;
        scrollOffset = clamp(scrollOffset - (float) amount * WHEEL_STEP);
        if (scrollOffset == before) {
            // 已到边界：不消费，让父级滚动容器继续处理
            return false;
        }
        layout(x, y);
        return true;
    }

    private float clamp(float value) {
        float max = Math.max(0f, contentHeight - height);
        return Math.max(0f, Math.min(value, max));
    }
}
