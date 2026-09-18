package com.mineui.ui.tree;

import com.mineui.ui.spec.DoubleSpec;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderNodeTest {

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

    private static final StateAccess STATE = (path, fallback) -> fallback;

    private static SliderNode slider(double min, double max, DoubleSpec value) {
        return new SliderNode(NodeStyle.defaults(), value, min, max, "set", "音量: {value}", 0xFFFFFFFF);
    }

    private static void layout(SliderNode node) {
        node.measure(new MeasureContext(200, 200, 200, 200, TEXT, STATE));
        node.layout(10, 10);
    }

    @Test
    void defaultSize() {
        SliderNode node = slider(0, 100, DoubleSpec.of(50));
        node.measure(new MeasureContext(200, 200, 200, 200, TEXT, STATE));
        assertEquals(160, node.width(), 0.01);
        assertEquals(20, node.height(), 0.01);
    }

    @Test
    void valueFromXClampsToRange() {
        SliderNode node = slider(0, 100, DoubleSpec.of(0));
        layout(node);

        assertEquals(0, node.valueFromX(10), 0.5);
        assertEquals(100, node.valueFromX(300), 0.5);
        assertEquals(50, node.valueFromX(10 + 4 + (160 - 8) / 2.0), 1.0);
    }

    @Test
    void ratioFollowsStateBinding() {
        SliderNode node = slider(0, 100, DoubleSpec.of(25));
        layout(node);
        assertEquals(0.25, node.ratio(STATE), 0.001);
    }

    @Test
    void dragLifecycle() {
        SliderNode node = slider(0, 100, DoubleSpec.of(0));
        layout(node);

        assertTrue(node.beginDrag(30, 20));
        assertTrue(node.dragging());
        assertTrue(node.ratio(STATE) > 0);

        assertTrue(node.dragTo(162));
        assertTrue(node.value(STATE) > 90);

        Double value = node.finishDrag();
        assertTrue(value != null && value > 90);
        assertFalse(node.dragging());
        assertNull(node.finishDrag());
    }

    @Test
    void beginDragOutsideReturnsFalse() {
        SliderNode node = slider(0, 100, DoubleSpec.of(0));
        layout(node);
        assertFalse(node.beginDrag(300, 300));
    }

    @Test
    void dragValueTakesPriorityOverState() {
        SliderNode node = slider(0, 100, DoubleSpec.of(0));
        layout(node);
        assertTrue(node.beginDrag(30, 20));
        assertTrue(node.dragTo(300));
        assertEquals(100, node.value(STATE), 0.001);
    }

    @Test
    void labelFormatsValue() {
        SliderNode node = slider(0, 100, DoubleSpec.of(50));
        assertEquals("音量: 50", node.label(STATE));

        SliderNode decimal = slider(0, 1, DoubleSpec.of(0.25));
        assertEquals("音量: 0.25", decimal.label(STATE));
    }

    @Test
    void stepSnapsDraggedValue() {
        SliderNode node = new SliderNode(NodeStyle.defaults(), DoubleSpec.of(0), 0, 100, "set", "", 0xFFFFFFFF,
                10, com.mineui.ui.spec.BooleanSpec.TRUE);
        layout(node);

        assertTrue(node.beginDrag(50, 20));
        double value = node.valueFromX(10 + 4 + (160 - 8) / 3.0);
        assertEquals(0, value % 10, 0.001, "应吸附到 10 的倍数");
    }

    @Test
    void disabledIgnoresDragAndClick() {
        SliderNode node = new SliderNode(NodeStyle.defaults(), DoubleSpec.of(0), 0, 100, "set", "", 0xFFFFFFFF,
                0, com.mineui.ui.spec.BooleanSpec.FALSE);
        layout(node);

        assertFalse(node.enabledNow());
        assertFalse(node.beginDrag(50, 20));
        assertNull(node.mouseClicked(60, 20, 0));
    }

    @Test
    void enabledBindingFollowsState() {
        SliderNode node = new SliderNode(NodeStyle.defaults(), DoubleSpec.of(0), 0, 100, "set", "", 0xFFFFFFFF,
                0, com.mineui.ui.spec.BooleanSpec.parse(new com.google.gson.JsonPrimitive("{state.on}")));
        node.measure(new MeasureContext(200, 200, 200, 200, TEXT, (path, fallback) -> "false"));
        assertFalse(node.enabledNow());
        node.measure(new MeasureContext(200, 200, 200, 200, TEXT, (path, fallback) -> "true"));
        assertTrue(node.enabledNow());
    }

    @Test
    void mouseClickHitsInsideOnly() {
        SliderNode node = slider(0, 100, DoubleSpec.of(0));
        layout(node);
        assertEquals(node, node.mouseClicked(60, 20, 0));
        assertNull(node.mouseClicked(300, 300, 0));
    }
}
