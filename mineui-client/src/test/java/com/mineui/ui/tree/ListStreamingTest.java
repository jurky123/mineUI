package com.mineui.ui.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import com.mineui.ui.spec.UiSpecParser;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListStreamingTest {

    private static final String FIVE_ITEMS =
            "[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"},{\"text\":\"d\"},{\"text\":\"e\"}]";

    /** 假字体：每字符 6px，行高 9px。 */
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

    private static NodeStyle style(SizeSpec width, SizeSpec height) {
        return new NodeStyle(null, width, height, Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f);
    }

    private static MeasureContext ctx(StateAccess state, float width, float height) {
        return new MeasureContext(width, height, width, height, TEXT, state);
    }

    /** 视口 100×40，条目固定 10 高；stickToBottom 走新构造器，否则走旧重载。 */
    private static ListViewNode list(boolean stickToBottom, float itemGap) {
        Supplier<UiNode> factory = () -> new TextNode(
                style(SizeSpec.px(80), SizeSpec.px(10)), "{item.text}", 0xFFFFFFFF, 1f);
        NodeStyle listStyle = style(SizeSpec.px(100), SizeSpec.px(40));
        if (stickToBottom) {
            return new ListViewNode(listStyle, "items", factory, null, null, false, itemGap, true);
        }
        return new ListViewNode(listStyle, "items", factory, null, null, false, itemGap);
    }

    private static JsonState state(String itemsJson) {
        return new JsonState(JsonParser.parseString("{\"items\":" + itemsJson + "}").getAsJsonObject());
    }

    private static JsonObject textItem(String text) {
        JsonObject item = new JsonObject();
        item.addProperty("text", text);
        return item;
    }

    @Test
    void stickToBottomFollowsAppendedItems() {
        JsonState state = state("[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"}]");
        ListViewNode list = list(true, 2f);

        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        assertTrue(list.following());
        // 3 条内容 34 < 视口 40，底部偏移为 0
        assertEquals(0f, list.scrollOffset(), 0.001f);

        state.append("d");
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        // 4 条内容 46 > 视口 40：跟到底部，最后一条仍落在视口内
        assertEquals(6f, list.scrollOffset(), 0.001f);
        assertEquals(list.contentHeight() - list.height(), list.scrollOffset(), 0.001f);
        assertTrue(list.following());
        UiNode last = list.children().get(3);
        assertEquals(30f, last.y(), 0.001f);
        assertTrue(last.y() >= 0f);
        assertTrue(last.y() + last.height() <= list.height() + 0.001f);
    }

    @Test
    void unchangedItemsKeepTheirNodeInstances() {
        JsonState state = state("[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"}]");
        ListViewNode list = list(true, 2f);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        UiNode first = list.children().get(0);
        UiNode second = list.children().get(1);
        UiNode third = list.children().get(2);

        state.update(1, "B");
        list.measure(ctx(state, 100, 100));

        assertEquals(3, list.children().size());
        assertSame(first, list.children().get(0));
        assertNotSame(second, list.children().get(1));
        assertSame(third, list.children().get(2));
    }

    @Test
    void wheelScrollUpPausesFollowAndScrollingBackResumesIt() {
        JsonState state = state(FIVE_ITEMS);
        ListViewNode list = list(true, 2f);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        // 5 条内容 58，视口 40 → 底部偏移 18
        assertEquals(18f, list.scrollOffset(), 0.001f);
        assertTrue(list.following());

        assertTrue(list.scroll(50, 20, 1)); // 上滚 24px，钳制到 0
        assertEquals(0f, list.scrollOffset(), 0.001f);
        assertFalse(list.following());

        assertTrue(list.scroll(50, 20, -999)); // 一路滚回底部
        assertEquals(18f, list.scrollOffset(), 0.001f);
        assertTrue(list.following());
    }

    @Test
    void scrollToBottomRestoresFollowing() {
        JsonState state = state(FIVE_ITEMS);
        ListViewNode list = list(true, 2f);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        list.scrollTo(0);
        assertFalse(list.following());

        list.scrollToBottom();
        assertEquals(18f, list.scrollOffset(), 0.001f);
        assertTrue(list.following());
    }

    @Test
    void nonStickListKeepsOffsetAndClampsWhenContentShrinks() {
        JsonState state = state(FIVE_ITEMS);
        ListViewNode list = list(false, 2f);

        assertFalse(list.stickToBottom());
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);
        assertFalse(list.following());

        list.scrollTo(6f);
        assertEquals(6f, list.scrollOffset(), 0.001f);

        // 追加内容只增长可滚动范围，不把用户拉回底部
        state.append("f");
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);
        assertEquals(6f, list.scrollOffset(), 0.001f);
        assertFalse(list.following());

        // 内容缩到比视口还短：旧偏移必须钳制回 0
        state.shrinkTo(2);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);
        assertEquals(0f, list.scrollOffset(), 0.001f);
    }

    /** 简易 JSON 状态：get/getElement 支持点分路径，并暴露可变 items 数组。 */
    private static final class JsonState implements StateAccess {

        private final JsonObject root;

        JsonState(JsonObject root) {
            this.root = root;
        }

        JsonArray items() {
            return root.getAsJsonArray("items");
        }

        void append(String text) {
            items().add(textItem(text));
        }

        void update(int index, String text) {
            items().set(index, textItem(text));
        }

        void shrinkTo(int size) {
            JsonArray array = items();
            while (array.size() > size) {
                array.remove(array.size() - 1);
            }
        }

        @Override
        public String get(String path, String defaultValue) {
            JsonElement element = getElement(path);
            return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
        }

        @Override
        public JsonElement getElement(String path) {
            JsonElement element = root;
            for (String part : path.split("\\.")) {
                if (element instanceof JsonObject object && object.has(part)) {
                    element = object.get(part);
                } else {
                    return null;
                }
            }
            return element;
        }
    }

    @Test
    void stickToBottomTakesPriorityOverHighlightCentering() {
        JsonState state = state(FIVE_ITEMS);
        state.root.addProperty("current", 0);
        ListViewNode list = new ListViewNode(
                style(SizeSpec.px(100), SizeSpec.px(40)), "items",
                () -> new TextNode(style(SizeSpec.px(80), SizeSpec.px(10)), "{item.text}", 0xFFFFFFFF, 1f),
                "current", 0xFF00FF00, true, 2f, true);

        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        // 跟随时底部优先：高亮 index 0 不会把视图拉回顶部，也不应关掉跟随
        assertTrue(list.following());
        assertEquals(18f, list.scrollOffset(), 0.001f);
    }

    @Test
    void listInGrowColumnUsesFinalViewportForFollow() throws Exception {
        String itemsJson = "[" + java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "{\"text\":\"" + i + "\"}")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        JsonState state = state(itemsJson);
        ColumnNode root = (ColumnNode) UiSpecParser.parse(JsonParser.parseString("""
                {"type":"column","width":200,"height":200,"gap":0,"children":[
                  {"type":"box","height":20},
                  {"type":"list","width":200,"grow":1,"items":"{state.items}","stickToBottom":true,
                   "itemTemplate":{"type":"box","width":200,"height":10}},
                  {"type":"box","height":20}
                ]}
                """).getAsJsonObject());

        root.measure(ctx(state, 200, 200));
        root.layout(0, 0);
        ListViewNode list = (ListViewNode) root.children().get(1);

        assertEquals(160f, list.height(), 0.001f);
        assertEquals(300f, list.contentHeight(), 0.001f);
        assertEquals(140f, list.scrollOffset(), 0.001f);
        assertTrue(list.following());

        state.append("new");
        root.measure(ctx(state, 200, 200));
        root.layout(0, 0);
        assertEquals(150f, list.scrollOffset(), 0.001f);
    }
}
