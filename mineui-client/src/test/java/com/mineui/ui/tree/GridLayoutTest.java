package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridLayoutTest {

    private static final TextMeasurer TEXT = new TextMeasurer() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    private static MeasureContext ctx(float width, float height) {
        return new MeasureContext(width, height, width, height, TEXT, StateAccess.EMPTY);
    }

    private static NodeStyle style(SizeSpec width, SizeSpec height, float gap) {
        return new NodeStyle(null, width, height, Insets.ZERO, null, CrossAlign.START, MainAlign.START, gap);
    }

    private static BoxNode cell(float height) {
        return new BoxNode(style(SizeSpec.auto(), SizeSpec.px(height), 0f));
    }

    @Test
    void measuresRowsAndStretchesCellWidth() {
        GridNode grid = new GridNode(style(SizeSpec.px(320), SizeSpec.auto(), 6f), 3, 6f);
        for (int i = 0; i < 6; i++) {
            grid.addChild(cell(28));
        }

        grid.measure(ctx(320, 400));
        assertEquals(320, grid.width(), 0.01);
        assertEquals(28 * 2 + 6, grid.height(), 0.01);

        grid.layout(0, 0);
        float cellWidth = (320f - 6f * 2) / 3f;
        assertEquals(cellWidth, grid.children().get(0).width(), 0.01);
        assertEquals(0, grid.children().get(0).x(), 0.01);
        assertEquals(cellWidth + 6, grid.children().get(1).x(), 0.01);
        assertEquals(2 * (cellWidth + 6), grid.children().get(2).x(), 0.01);
        assertEquals(34, grid.children().get(3).y(), 0.01);
        assertEquals(34, grid.children().get(4).y(), 0.01);
    }

    @Test
    void lastRowPartialIsCounted() {
        GridNode grid = new GridNode(style(SizeSpec.px(100), SizeSpec.auto(), 0f), 2, 4f);
        grid.addChild(cell(10));
        grid.addChild(cell(10));
        grid.addChild(cell(10));

        grid.measure(ctx(100, 100));
        assertEquals(10 + 4 + 10, grid.height(), 0.01);
    }

    @Test
    void rowHeightUsesTallestChild() {
        GridNode grid = new GridNode(style(SizeSpec.px(100), SizeSpec.auto(), 0f), 2, 0f);
        grid.addChild(cell(10));
        grid.addChild(cell(30));
        grid.addChild(cell(10));
        grid.addChild(cell(10));

        grid.measure(ctx(100, 100));
        assertEquals(30 + 10, grid.height(), 0.01);

        grid.layout(0, 0);
        assertEquals(30, grid.children().get(2).y(), 0.01);
    }

    @Test
    void centerAlignsChildrenVerticallyInRow() {
        NodeStyle style = new NodeStyle(null, SizeSpec.px(100), SizeSpec.auto(), Insets.ZERO, null,
                CrossAlign.CENTER, MainAlign.START, 0f);
        GridNode grid = new GridNode(style, 2, 0f);
        grid.addChild(cell(10));
        grid.addChild(cell(30));

        grid.measure(ctx(100, 100));
        grid.layout(0, 0);
        assertEquals(10, grid.children().get(0).y(), 0.01);
        assertEquals(0, grid.children().get(1).y(), 0.01);
    }
}
