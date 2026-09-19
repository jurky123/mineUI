package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActionIndexTest {

    private static NodeStyle style(SizeSpec width, SizeSpec height) {
        return new NodeStyle(null, width, height, Insets.ZERO, null, CrossAlign.START, MainAlign.START, 0f);
    }

    private static MeasureContext ctx(StateAccess state) {
        TextMeasurer text = new TextMeasurer() {
            @Override
            public int width(String s) {
                return s.length() * 6;
            }

            @Override
            public int lineHeight() {
                return 9;
            }
        };
        return new MeasureContext(300, 300, 300, 300, text, state);
    }

    @Test
    void itemRootCarriesIndexAndChildrenWalkUp() {
        // 条目根节点点击：直接命中
        StateAccess state = ListLayoutTest.state("{\"lyrics\":[\"a\",\"b\"],\"current\":0}");
        ListViewNode list = new ListViewNode(style(SizeSpec.px(100), SizeSpec.px(40)), "lyrics",
                () -> {
                    TextNode text = new TextNode(style(null, null), "{item}", 0xFFFFFFFF, 1f);
                    return text;
                },
                "current", null, false, 0f);
        list.measure(ctx(state));
        list.layout(0, 0);

        assertEquals(2, list.children().size());
        assertEquals(0, list.children().get(0).getItemIndex());
        assertEquals(1, list.children().get(1).getItemIndex());

        // 命中条目内部（模板为容器时）：祖先链上找到条目下标
        ColumnNode itemRoot = new ColumnNode(style(null, null));
        TextNode inner = new TextNode(NodeStyle.defaults(), "x", 0xFFFFFFFF, 1f);
        itemRoot.addChild(inner);
        itemRoot.setItemIndex(1);
        assertEquals(1, inner.enclosingItemIndex(), "从子节点沿父链找到条目下标");
        assertEquals(-1, new TextNode(NodeStyle.defaults(), "x", 0xFFFFFFFF, 1f).enclosingItemIndex(), "无父链时为 -1");
    }

    @Test
    void tabsClickRecordsIndex() {
        StateAccess state = ListLayoutTest.state("{\"tab\": 0}");
        TabsNode tabs = new TabsNode(style(SizeSpec.px(200), SizeSpec.px(20)),
                List.of("A", "BB", "CCC"),
                com.mineui.ui.spec.DoubleSpec.parse(new com.google.gson.JsonPrimitive("{state.tab}"), 0),
                "tab_select");
        tabs.measure(ctx(state));
        tabs.layout(0, 0);

        assertEquals(200, tabs.width(), 0.01);
        // A: 1*6+16=22；BB: 2*6+16=28 → 第二页从 22 到 50
        tabs.mouseClicked(30, 10, 0);
        assertEquals(1, tabs.clickedIndex());
        assertEquals(1, tabs.getItemIndex());

        tabs.mouseClicked(10, 10, 0);
        assertEquals(0, tabs.clickedIndex());
    }

    @Test
    void outsideHitDoesNotRecord() {
        StateAccess state = ListLayoutTest.state("{}");
        TabsNode tabs = new TabsNode(style(SizeSpec.px(200), SizeSpec.px(20)), List.of("A"),
                com.mineui.ui.spec.DoubleSpec.of(0), "tab_select");
        tabs.measure(ctx(state));
        tabs.layout(0, 0);
        assertNull(tabs.mouseClicked(500, 500, 0));
    }
}
