package com.mineui.ui.tree;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityTest {

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

    private static MeasureContext ctx(StateAccess state) {
        return new MeasureContext(200, 200, 200, 200, TEXT, state);
    }

    private static NodeStyle style(BooleanSpec visible) {
        return new NodeStyle("n", SizeSpec.auto(), SizeSpec.auto(), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f, 0f, null, 0f, null, 0f, 0f,
                null, 0, false, false, visible, null, false, null, null, null, null, null);
    }

    @Test
    void invisibleLeafHasZeroSize() {
        TextNode text = new TextNode(style(BooleanSpec.FALSE), "hello", 0xFFFFFFFF, 1f);
        text.measure(ctx(StateAccess.EMPTY));
        assertFalse(text.visibleNow());
        assertEquals(0, text.width(), 0.01);
        assertEquals(0, text.height(), 0.01);
    }

    @Test
    void invisibleChildrenDoNotAddToContainerSize() {
        ColumnNode column = new ColumnNode(style(BooleanSpec.TRUE));
        column.addChild(new TextNode(style(BooleanSpec.TRUE), "hello", 0xFFFFFFFF, 1f));
        column.addChild(new TextNode(style(BooleanSpec.FALSE), "worldworld", 0xFFFFFFFF, 1f));

        column.measure(ctx(StateAccess.EMPTY));
        assertEquals(30, column.width(), 0.01);
        assertEquals(9, column.height(), 0.01);
    }

    @Test
    void bindingVisibilityFollowsState() {
        BooleanSpec binding = BooleanSpec.parse(new JsonPrimitive("{state.show}"));
        ColumnNode column = new ColumnNode(style(binding));

        column.measure(ctx((path, fallback) -> "true"));
        assertTrue(column.visibleNow());
        assertEquals(0, column.children().size());
        assertTrue(column.width() >= 0);

        column.measure(ctx((path, fallback) -> "false"));
        assertFalse(column.visibleNow());
        assertEquals(0, column.width(), 0.01);
    }

    @Test
    void modalBlocksClicksToLowerNodes() {
        StackNode root = new StackNode(style(BooleanSpec.TRUE));
        ButtonNode button = new ButtonNode(style(BooleanSpec.TRUE), "go", "action", 0, 0, 0xFFFFFFFF);
        button.overrideWidth(50);
        button.overrideHeight(20);

        NodeStyle modalStyle = new NodeStyle("modal", SizeSpec.px(200), SizeSpec.px(200), Insets.ZERO,
                0, CrossAlign.START, MainAlign.START, 0f, 0f, null, 0f, null, 0f, 0f,
                null, 10, false, false, BooleanSpec.TRUE, null, true, null, null, null, null, null);
        BoxNode modal = new BoxNode(modalStyle);
        modal.overrideWidth(200);
        modal.overrideHeight(200);

        root.addChild(button);
        root.addChild(modal);
        root.overrideWidth(200);
        root.overrideHeight(200);
        root.setVisibleNow(true);
        button.setVisibleNow(true);
        modal.setVisibleNow(true);
        button.layout(0, 0);
        modal.layout(0, 0);

        assertEquals(modal, root.mouseClicked(10, 10, 0));

        modal.setVisibleNow(false);
        assertEquals(button, root.mouseClicked(10, 10, 0));
    }

    @Test
    void tooltipPrefersDeepestVisibleNode() {
        ColumnNode root = new ColumnNode(style(BooleanSpec.TRUE));
        NodeStyle tooltipStyle = new NodeStyle("t", SizeSpec.px(100), SizeSpec.px(50), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f, 0f, null, 0f, null, 0f, 0f,
                null, 0, false, false, BooleanSpec.TRUE, "提示文本", false, null, null, null, null, null);
        BoxNode child = new BoxNode(tooltipStyle);
        child.overrideWidth(100);
        child.overrideHeight(50);
        root.addChild(child);

        root.overrideWidth(200);
        root.overrideHeight(200);
        root.setVisibleNow(true);
        child.setVisibleNow(true);
        root.layout(0, 0);
        child.layout(0, 0);

        assertEquals(child, root.findTooltip(10, 10));
        assertNull(root.findTooltip(150, 150));
    }
}
