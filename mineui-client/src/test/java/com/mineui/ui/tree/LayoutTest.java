package com.mineui.ui.tree;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutTest {

    /** 假文本测量：每字符 6px，行高 9px。 */
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

    private static NodeStyle style(SizeSpec width, SizeSpec height, Insets padding,
                                   CrossAlign align, MainAlign justify, float gap) {
        return new NodeStyle(null, width, height, padding, null, align, justify, gap);
    }

    private static TextNode text(String content) {
        return new TextNode(NodeStyle.defaults(), content, 0xFFFFFFFF, 1f);
    }

    @Test
    void columnAutoSizesToChildrenWithPaddingAndGap() {
        ColumnNode column = new ColumnNode(style(SizeSpec.auto(), SizeSpec.auto(), Insets.all(10),
                CrossAlign.START, MainAlign.START, 5f));
        TextNode first = text("abcd");
        TextNode second = text("ab");
        column.addChild(first);
        column.addChild(second);

        column.measure(ctx(200, 200));
        assertEquals(24 + 20, column.width(), 0.01);
        assertEquals(9 + 5 + 9 + 20, column.height(), 0.01);

        column.layout(0, 0);
        assertEquals(10, first.x(), 0.01);
        assertEquals(10, first.y(), 0.01);
        assertEquals(10 + 9 + 5, second.y(), 0.01);
    }

    @Test
    void columnJustifyCenterAndCrossCenter() {
        ColumnNode column = new ColumnNode(style(SizeSpec.px(100), SizeSpec.px(100), Insets.ZERO,
                CrossAlign.CENTER, MainAlign.CENTER, 0f));
        TextNode child = text("ab");
        column.addChild(child);

        column.measure(ctx(100, 100));
        column.layout(0, 0);
        assertEquals(44, child.x(), 0.01);
        assertEquals(45.5, child.y(), 0.01);
    }

    @Test
    void rowSizesAlongMainAxisWithGap() {
        RowNode row = new RowNode(style(SizeSpec.auto(), SizeSpec.auto(), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 4f));
        TextNode first = text("ab");
        TextNode second = text("abcd");
        row.addChild(first);
        row.addChild(second);

        row.measure(ctx(200, 200));
        assertEquals(12 + 4 + 24, row.width(), 0.01);
        assertEquals(9, row.height(), 0.01);

        row.layout(0, 0);
        assertEquals(0, first.x(), 0.01);
        assertEquals(16, second.x(), 0.01);
    }

    @Test
    void percentWidthResolvesAgainstExplicitParentWidth() throws Exception {
        ColumnNode column = new ColumnNode(style(SizeSpec.px(200), SizeSpec.auto(), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 0f));
        TextNode percent = new TextNode(style(SizeSpec.parse(new JsonPrimitive("50%")), SizeSpec.auto(),
                Insets.ZERO, CrossAlign.START, MainAlign.START, 0f), "ab", 0xFFFFFFFF, 1f);
        column.addChild(percent);

        column.measure(ctx(400, 400));
        assertEquals(100, percent.width(), 0.01);
    }

    @Test
    void buttonAutoSizeIsTextPlusPadding() {
        ButtonNode button = new ButtonNode(NodeStyle.defaults(), "go", "action", 0, 0, 0xFFFFFFFF);
        button.measure(ctx(200, 200));
        assertEquals(12 + 16, button.width(), 0.01);
        assertEquals(9 + 8, button.height(), 0.01);
    }

    @Test
    void stretchFillsCrossAxisForAutoWidth() {
        ColumnNode column = new ColumnNode(style(SizeSpec.px(100), SizeSpec.auto(), Insets.ZERO,
                CrossAlign.STRETCH, MainAlign.START, 0f));
        ButtonNode button = new ButtonNode(NodeStyle.defaults(), "go", "action", 0, 0, 0xFFFFFFFF);
        column.addChild(button);

        column.measure(ctx(100, 100));
        column.layout(0, 0);
        assertEquals(100, button.width(), 0.01);
        assertEquals(0, button.x(), 0.01);
    }

    @Test
    void stackAlignsChildren() {
        StackNode stack = new StackNode(style(SizeSpec.px(100), SizeSpec.px(50), Insets.ZERO,
                CrossAlign.CENTER, MainAlign.CENTER, 0f));
        BoxNode box = new BoxNode(style(SizeSpec.px(20), SizeSpec.px(10), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 0f));
        stack.addChild(box);

        stack.measure(ctx(100, 50));
        stack.layout(0, 0);
        assertEquals(40, box.x(), 0.01);
        assertEquals(20, box.y(), 0.01);
    }

    @Test
    void textWidthUsesStateBinding() {
        StateAccess state = (path, fallback) -> path.equals("name") ? "abcdef" : fallback;
        MeasureContext context = new MeasureContext(200, 200, 200, 200, TEXT, state);
        TextNode node = new TextNode(NodeStyle.defaults(), "{state.name}", 0xFFFFFFFF, 1f);

        node.measure(context);
        assertEquals(36, node.width(), 0.01);
    }

    @Test
    void textScaleMultipliesSize() {
        TextNode node = new TextNode(NodeStyle.defaults(), "ab", 0xFFFFFFFF, 2f);
        node.measure(ctx(200, 200));
        assertEquals(24, node.width(), 0.01);
        assertEquals(18, node.height(), 0.01);
    }

    @Test
    void explicitSizeOverridesIntrinsicSize() {
        TextNode node = new TextNode(style(SizeSpec.px(80), SizeSpec.px(30), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 0f), "ab", 0xFFFFFFFF, 1f);
        node.measure(ctx(200, 200));
        assertEquals(80, node.width(), 0.01);
        assertEquals(30, node.height(), 0.01);
    }

    @Test
    void clickHitsButtonButNotEmptyArea() {
        ColumnNode column = new ColumnNode(style(SizeSpec.px(100), SizeSpec.px(100), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 0f));
        ButtonNode button = new ButtonNode(style(SizeSpec.px(50), SizeSpec.px(20), Insets.ZERO,
                CrossAlign.START, MainAlign.START, 0f), "go", "action", 0, 0, 0xFFFFFFFF);
        column.addChild(button);

        column.measure(ctx(100, 100));
        column.layout(0, 0);

        assertEquals(button, column.mouseClicked(10, 10, 0));
        assertEquals(null, column.mouseClicked(90, 90, 0));
    }
}
