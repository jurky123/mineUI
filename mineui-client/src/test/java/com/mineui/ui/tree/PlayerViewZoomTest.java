package com.mineui.ui.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerViewZoomTest {

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

    private static PlayerViewNode node(boolean zoomable, double min, double max) {
        return new PlayerViewNode(NodeStyle.defaults(), "@self", 30f, true, null, null, zoomable, min, max);
    }

    private static void layout(PlayerViewNode node) {
        node.measure(new MeasureContext(200, 200, 200, 200, TEXT, STATE));
        node.layout(10, 10);
    }

    @Test
    void defaultZoomIsOne() {
        assertEquals(1.0, node(true, 0.5, 2.0).zoom(), 0.0001);
    }

    @Test
    void nonZoomableDoesNotConsumeScroll() {
        PlayerViewNode node = node(false, 0.5, 2.0);
        layout(node);
        assertFalse(node.scroll(20, 20, 1));
        assertEquals(1.0, node.zoom(), 0.0001);
    }

    @Test
    void scrollInsideConsumesAndZoomsByTenPercent() {
        PlayerViewNode node = node(true, 0.5, 2.0);
        layout(node);

        assertTrue(node.scroll(20, 20, 1));
        assertEquals(1.1, node.zoom(), 0.0001);

        assertTrue(node.scroll(20, 20, -1));
        assertEquals(1.1 * 0.9, node.zoom(), 0.0001);
    }

    @Test
    void zoomClampsToMaxAndMin() {
        PlayerViewNode node = node(true, 0.5, 2.0);
        layout(node);

        for (int i = 0; i < 50; i++) {
            node.scroll(20, 20, 1);
        }
        assertEquals(2.0, node.zoom(), 0.0001);

        for (int i = 0; i < 100; i++) {
            node.scroll(20, 20, -1);
        }
        assertEquals(0.5, node.zoom(), 0.0001);
    }

    @Test
    void scrollOutsideIsNotConsumed() {
        PlayerViewNode node = node(true, 0.5, 2.0);
        layout(node);
        assertFalse(node.scroll(500, 500, 1));
        assertEquals(1.0, node.zoom(), 0.0001);
    }

    @Test
    void zoomDoesNotAffectLayout() {
        PlayerViewNode node = node(true, 0.5, 2.0);
        layout(node);
        float width = node.width();
        float height = node.height();

        node.zoomBy(5);
        assertEquals(width, node.width(), 0.0001);
        assertEquals(height, node.height(), 0.0001);
    }

    @Test
    void zoomByClampsDirectly() {
        PlayerViewNode node = node(true, 0.5, 2.0);
        node.zoomBy(100);
        assertEquals(2.0, node.zoom(), 0.0001);
        node.zoomBy(-100);
        assertEquals(0.5, node.zoom(), 0.0001);
    }
}
