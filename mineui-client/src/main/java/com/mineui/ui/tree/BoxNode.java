package com.mineui.ui.tree;

/** 纯色矩形节点（background 为空时用作占位/间隔）。 */
public final class BoxNode extends UiNode {

    public BoxNode(NodeStyle style) {
        super(style);
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = Math.max(0, resolvedW);
        height = Math.max(0, resolvedH);
    }
}
