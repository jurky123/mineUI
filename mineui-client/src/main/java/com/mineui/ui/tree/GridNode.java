package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;

import java.util.List;

/** 网格容器：固定列数，单元格等宽，行高取该行最高子节点。 */
public final class GridNode extends ContainerNode {

    private final int columns;
    private final float rowGap;

    public GridNode(NodeStyle style, int columns, float rowGap) {
        super(style);
        this.columns = Math.max(1, columns);
        this.rowGap = Math.max(0f, rowGap);
    }

    public int columns() {
        return columns;
    }

    public float rowGap() {
        return rowGap;
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
        float contentW = Math.max(0f, (resolvedW >= 0 ? resolvedW : context.availableWidth()) - pad.horizontal());
        float cellW = Math.max(0f, (contentW - gap() * (columns - 1)) / columns);

        List<UiNode> kids = children();
        int rows = (kids.size() + columns - 1) / columns;
        float[] rowHeights = new float[Math.max(0, rows)];
        for (int i = 0; i < kids.size(); i++) {
            UiNode child = kids.get(i);
            child.measure(context.withAvailable(cellW, context.availableHeight()));
            if (!child.visibleNow()) {
                continue;
            }
            int row = i / columns;
            rowHeights[row] = Math.max(rowHeights[row], child.height());
        }
        float totalH = 0f;
        for (float rowHeight : rowHeights) {
            totalH += rowHeight;
        }
        if (rows > 1) {
            totalH += rowGap * (rows - 1);
        }

        width = resolvedW >= 0 ? resolvedW : pad.horizontal() + contentW;
        height = resolvedH >= 0 ? resolvedH : pad.vertical() + totalH;
    }

    @Override
    public void layout(float x, float y) {
        super.layout(x, y);
        Insets pad = style().padding();
        float contentW = Math.max(0f, width - pad.horizontal());
        float cellW = Math.max(0f, (contentW - gap() * (columns - 1)) / columns);

        List<UiNode> kids = children();
        int rows = (kids.size() + columns - 1) / columns;
        float[] rowHeights = new float[Math.max(0, rows)];
        for (int i = 0; i < kids.size(); i++) {
            UiNode child = kids.get(i);
            if (!child.visibleNow()) {
                continue;
            }
            int row = i / columns;
            rowHeights[row] = Math.max(rowHeights[row], child.height());
        }

        float cursorY = y + pad.top();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= kids.size()) {
                    break;
                }
                UiNode child = kids.get(index);
                if (!child.visibleNow()) {
                    continue;
                }
                if (child.style().width().isAuto()) {
                    child.overrideWidth(cellW);
                }
                if (style().align() == CrossAlign.STRETCH && child.style().height().isAuto()) {
                    child.overrideHeight(rowHeights[row]);
                }
                float childY = cursorY;
                if (style().align() == CrossAlign.CENTER) {
                    childY += (rowHeights[row] - child.height()) / 2f;
                } else if (style().align() == CrossAlign.END) {
                    childY += rowHeights[row] - child.height();
                }
                child.layout(x + pad.left() + col * (cellW + gap()), childY);
            }
            cursorY += rowHeights[row] + rowGap;
        }
    }
}
