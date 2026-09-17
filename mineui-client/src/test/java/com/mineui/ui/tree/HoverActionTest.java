package com.mineui.ui.tree;

import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoverActionTest {

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

    private static NodeStyle hoverStyle(String hoverAction) {
        return new NodeStyle("h", SizeSpec.px(50), SizeSpec.px(20), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f, 0f, null, 0f, null, 0f, 0f,
                null, 0, false, false, BooleanSpec.TRUE, null, false, null, hoverAction, null, null, null);
    }

    private static ColumnNode treeWithHoverBox(String hoverAction) {
        ColumnNode root = new ColumnNode(NodeStyle.defaults());
        BoxNode box = new BoxNode(hoverStyle(hoverAction));
        root.addChild(box);
        root.measure(new MeasureContext(200, 200, 200, 200, TEXT, StateAccess.EMPTY));
        root.layout(0, 0);
        root.setVisibleNow(true);
        box.setVisibleNow(true);
        return root;
    }

    private static List<String> collect(UiNode root) {
        List<String> actions = new ArrayList<>();
        root.collectHoverActions(actions);
        return actions;
    }

    @Test
    void emitsOnlyOnHoverEnter() {
        UiNode root = treeWithHoverBox("hover_item");

        root.mouseMoved(10, 10);
        assertEquals(List.of("hover_item"), collect(root));

        root.mouseMoved(11, 11);
        assertTrue(collect(root).isEmpty(), "持续悬停不应重复触发");

        root.mouseMoved(500, 500);
        assertTrue(collect(root).isEmpty());

        root.mouseMoved(10, 10);
        assertEquals(List.of("hover_item"), collect(root), "离开后再进入应再次触发");
    }

    @Test
    void nodesWithoutHoverActionAreIgnored() {
        UiNode root = treeWithHoverBox(null);
        root.mouseMoved(10, 10);
        assertTrue(collect(root).isEmpty());
    }

    @Test
    void clearHoverAllowsReentryTrigger() {
        UiNode root = treeWithHoverBox("hover_item");
        root.mouseMoved(10, 10);
        assertEquals(1, collect(root).size());

        root.clearHover();
        root.mouseMoved(10, 10);
        assertEquals(List.of("hover_item"), collect(root));
    }

    @Test
    void hoverActionAccessor() {
        BoxNode box = new BoxNode(hoverStyle("x"));
        assertEquals("x", box.hoverAction());
        assertEquals("", new BoxNode(NodeStyle.defaults()).hoverAction());
    }
}
