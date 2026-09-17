package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;

import java.util.List;

/** 横向布局容器。 */
public final class RowNode extends ContainerNode {

    public RowNode(NodeStyle style) {
        super(style);
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        Insets pad = style().padding();
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float contentW = contentWidth(resolvedW, context.availableWidth(), pad);
        float contentH = contentHeight(resolvedH, context.availableHeight(), pad);

        List<UiNode> kids = children();
        float usedMain = 0;
        float maxCross = 0;
        int visibleCount = 0;
        for (UiNode child : kids) {
            child.measure(context.withAvailable(Math.max(0, contentW - usedMain), contentH));
            if (!child.visibleNow()) {
                continue;
            }
            usedMain += child.width();
            maxCross = Math.max(maxCross, child.height());
            visibleCount++;
        }
        if (visibleCount > 1) {
            usedMain += gap() * (visibleCount - 1);
        }

        width = resolvedW >= 0 ? resolvedW : pad.horizontal() + usedMain;
        height = resolvedH >= 0 ? resolvedH : pad.vertical() + maxCross;
    }

    @Override
    public void layout(float x, float y) {
        super.layout(x, y);
        Insets pad = style().padding();
        float contentX = x + pad.left();
        float contentY = y + pad.top();
        float contentW = Math.max(0, width - pad.horizontal());
        float contentH = Math.max(0, height - pad.vertical());

        List<UiNode> kids = children();
        float total = 0;
        int visibleCount = 0;
        for (UiNode child : kids) {
            if (!child.visibleNow()) {
                continue;
            }
            total += child.width();
            visibleCount++;
        }
        if (visibleCount > 1) {
            total += gap() * (visibleCount - 1);
        }

        float free = Math.max(0, contentW - total);
        float offset = switch (style().justify()) {
            case START, SPACE_BETWEEN -> 0;
            case CENTER -> free / 2;
            case END -> free;
        };
        float extraGap = style().justify() == MainAlign.SPACE_BETWEEN && visibleCount > 1
                ? free / (visibleCount - 1) : 0;
        CrossAlign align = style().align();

        float cursorX = contentX + offset;
        for (UiNode child : kids) {
            if (!child.visibleNow()) {
                continue;
            }
            if (align == CrossAlign.STRETCH && child.style().height().isAuto()) {
                child.overrideHeight(contentH);
            }
            float childH = child.height();
            float childY = switch (align) {
                case START, STRETCH -> contentY;
                case CENTER -> contentY + (contentH - childH) / 2;
                case END -> contentY + (contentH - childH);
            };
            child.layout(cursorX, childY);
            cursorX += child.width() + gap() + extraGap;
        }
    }
}
