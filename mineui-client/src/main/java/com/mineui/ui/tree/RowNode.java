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
        Insets pad = style().padding();
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float contentW = contentWidth(resolvedW, context.availableWidth(), pad);
        float contentH = contentHeight(resolvedH, context.availableHeight(), pad);

        float usedMain = 0;
        float maxCross = 0;
        List<UiNode> kids = children();
        for (UiNode child : kids) {
            child.measure(context.withAvailable(Math.max(0, contentW - usedMain), contentH));
            usedMain += child.width();
            maxCross = Math.max(maxCross, child.height());
        }
        if (kids.size() > 1) {
            usedMain += gap() * (kids.size() - 1);
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
        for (UiNode child : kids) {
            total += child.width();
        }
        if (kids.size() > 1) {
            total += gap() * (kids.size() - 1);
        }

        float free = Math.max(0, contentW - total);
        float offset = switch (style().justify()) {
            case START, SPACE_BETWEEN -> 0;
            case CENTER -> free / 2;
            case END -> free;
        };
        float extraGap = style().justify() == MainAlign.SPACE_BETWEEN && kids.size() > 1
                ? free / (kids.size() - 1) : 0;
        CrossAlign align = style().align();

        float cursorX = contentX + offset;
        for (UiNode child : kids) {
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
