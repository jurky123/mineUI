package com.mineui.ui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListLayoutTest {

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
        return new NodeStyle(null, width, height, Insets.ZERO, null, CrossAlign.START, MainAlign.START, 0f);
    }

    private static MeasureContext ctx(StateAccess state, float w, float h) {
        return new MeasureContext(w, h, w, h, TEXT, state);
    }

    private static JsonState state(String json) {
        return new JsonState(JsonParser.parseString(json).getAsJsonObject());
    }

    private static ListViewNode textList(StateAccess ignored, float itemHeight, boolean autoScroll) {
        return new ListViewNode(style(SizeSpec.px(100), SizeSpec.px(40)), "lyrics",
                () -> new TextNode(style(SizeSpec.px(80), SizeSpec.px(itemHeight)), "{item}", 0xFFFFFFFF, 1f),
                "current", 0xFF00FF00, autoScroll, 2f);
    }

    @Test
    void buildsItemChildrenFromStateArray() {
        JsonState state = state("{\"lyrics\":[\"a\",\"bb\",\"ccc\"],\"current\":0}");
        ListViewNode list = textList(state, 10, false);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        assertEquals(3, list.children().size());
        assertEquals(10 * 3 + 2 * 2, list.contentHeight(), 0.01);
    }

    @Test
    void itemTemplateResolvesItemFields() {
        JsonState state = state("{\"lyrics\":[{\"text\":\"x\",\"artist\":\"y\"}],\"current\":0}");
        ListViewNode list = new ListViewNode(style(SizeSpec.px(100), SizeSpec.px(40)), "lyrics",
                () -> new TextNode(style(null, null), "{item.text}-{item.artist}", 0xFFFFFFFF, 1f),
                "current", null, false, 0f);
        list.measure(ctx(state, 100, 100));

        assertEquals(1, list.children().size());
        // "x-y" 3 字符 × 6 = 18
        assertEquals(18, list.children().get(0).width(), 0.01);
    }

    @Test
    void highlightAutoCentersAndClamps() {
        JsonState state = state("{\"lyrics\":[\"0\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"],\"current\":5}");
        ListViewNode list = textList(state, 10, true);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);

        assertSame(list.children().get(5), list.highlightedItem());
        assertEquals(0xFF00FF00, list.highlightColor());
        // 10 项 ×(10 高 + 2 gap) = 118 高、视口 40：高亮项 5 中心 65 → 目标偏移 45（未越界）
        assertEquals(45, list.scrollOffset(), 0.01);
        assertEquals(15, list.children().get(5).y(), 0.01);
    }

    @Test
    void wheelScrollWorksAndClampsAfterHighlight() {
        JsonState state = state("{\"lyrics\":[\"0\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"],\"current\":5}");
        ListViewNode list = textList(state, 10, true);
        list.measure(ctx(state, 100, 100));
        list.layout(0, 0);
        assertTrue(list.scrollable());

        assertTrue(list.scroll(50, 20, 1)); // 向回滚 24
        assertEquals(45 - 24, list.scrollOffset(), 0.01);

        list.scrollTo(9999);
        assertEquals(78, list.scrollOffset(), 0.01); // 118 - 40
    }

    /** 简易 JSON 状态：get/getElement 支持点分路径。 */
    private static final class JsonState implements StateAccess {

        private final JsonObject root;

        JsonState(JsonObject root) {
            this.root = root;
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
}
