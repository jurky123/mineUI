package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollViewTest {

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

    private static NodeStyle style(SizeSpec width, SizeSpec height) {
        return new NodeStyle(null, width, height, Insets.ZERO, null, CrossAlign.START, MainAlign.START, 0f);
    }

    private static ScrollViewNode scrollWithItems(int count, float itemHeight) {
        ScrollViewNode scroll = new ScrollViewNode(style(SizeSpec.px(100), SizeSpec.px(50)));
        for (int i = 0; i < count; i++) {
            scroll.addChild(new BoxNode(style(SizeSpec.px(80), SizeSpec.px(itemHeight))));
        }
        return scroll;
    }

    @Test
    void contentTallerThanViewportIsScrollable() {
        ScrollViewNode scroll = scrollWithItems(10, 20);
        scroll.measure(ctx(100, 100));
        scroll.layout(0, 0);

        assertEquals(100, scroll.width(), 0.01);
        assertEquals(50, scroll.height(), 0.01);
        assertEquals(200, scroll.contentHeight(), 0.01);
        assertTrue(scroll.scrollable());
    }

    @Test
    void shortContentIsNotScrollable() {
        ScrollViewNode scroll = scrollWithItems(2, 10);
        scroll.measure(ctx(100, 100));
        assertFalse(scroll.scrollable());
    }

    @Test
    void wheelScrollsAndClamps() {
        ScrollViewNode scroll = scrollWithItems(10, 20);
        scroll.measure(ctx(100, 100));
        scroll.layout(0, 0);

        assertTrue(scroll.scroll(50, 25, -1));
        assertEquals(24, scroll.scrollOffset(), 0.01);
        assertEquals(-24, scroll.children().get(0).y(), 0.01);

        scroll.scrollTo(10000);
        assertEquals(150, scroll.scrollOffset(), 0.01);
        scroll.scrollTo(-5);
        assertEquals(0, scroll.scrollOffset(), 0.01);
    }

    @Test
    void scrollOutsideBoundsIsNotConsumed() {
        ScrollViewNode scroll = scrollWithItems(10, 20);
        scroll.measure(ctx(100, 100));
        scroll.layout(0, 0);
        assertFalse(scroll.scroll(200, 200, -1));
    }

    @Test
    void relayoutOnScrollMovesChildren() {
        ScrollViewNode scroll = scrollWithItems(10, 20);
        scroll.measure(ctx(100, 100));
        scroll.layout(0, 0);
        scroll.scrollTo(40);
        assertEquals(-40, scroll.children().get(0).y(), 0.01);
        assertEquals(20 - 40, scroll.children().get(1).y(), 0.01);
    }
}
