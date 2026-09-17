package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;

import java.util.List;

/** 层叠容器：子节点重叠，用 align（水平）与 justify（垂直）对齐。 */
public final class StackNode extends ContainerNode {

    public StackNode(NodeStyle style) {
        super(style);
    }

    @Override
    public void measure(MeasureContext context) {
        Insets pad = style().padding();
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float contentW = contentWidth(resolvedW, context.availableWidth(), pad);
        float contentH = contentHeight(resolvedH, context.availableHeight(), pad);

        float maxW = 0;
        float maxH = 0;
        for (UiNode child : children()) {
            child.measure(context.withAvailable(contentW, contentH));
            maxW = Math.max(maxW, child.width());
            maxH = Math.max(maxH, child.height());
        }

        width = resolvedW >= 0 ? resolvedW : pad.horizontal() + maxW;
        height = resolvedH >= 0 ? resolvedH : pad.vertical() + maxH;
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
        for (UiNode child : kids) {
            if (style().align() == CrossAlign.STRETCH && child.style().width().isAuto()) {
                child.overrideWidth(contentW);
            }
            if (style().justify() == MainAlign.SPACE_BETWEEN) {
                child.overrideHeight(contentH);
            }
            float childW = child.width();
            float childH = child.height();
            float childX = switch (style().align()) {
                case START, STRETCH -> contentX;
                case CENTER -> contentX + (contentW - childW) / 2;
                case END -> contentX + (contentW - childW);
            };
            float childY = switch (style().justify()) {
                case START, SPACE_BETWEEN -> contentY;
                case CENTER -> contentY + (contentH - childH) / 2;
                case END -> contentY + (contentH - childH);
            };
            child.layout(childX, childY);
        }
    }
}
