package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.ListViewNode;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanSpecItemTest {

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

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesItemIndexAndStatePaths() {
        BooleanSpec itemFlag = BooleanSpec.parse(new JsonPrimitive("{item.isUser}"));
        assertEquals("item.isUser", itemFlag.path());
        assertTrue(itemFlag.test(new ItemState(json("{\"isUser\":true}"), 1)));
        assertFalse(itemFlag.test(new ItemState(json("{\"isUser\":false}"), 1)));

        BooleanSpec itemIndex = BooleanSpec.parse(new JsonPrimitive("{itemIndex}"));
        assertEquals("itemIndex", itemIndex.path());
        assertTrue(itemIndex.test(new ItemState(json("{}"), 1)));
        assertFalse(itemIndex.test(new ItemState(json("{}"), 0)));

        BooleanSpec state = BooleanSpec.parse(new JsonPrimitive("{state.x}"));
        assertEquals("x", state.path());
        assertTrue(state.test((path, fallback) -> path.equals("x") ? "true" : fallback));
        assertFalse(state.test((path, fallback) -> path.equals("x") ? "false" : fallback));
    }

    @Test
    void itemBindingControlsVisibilityInsideListItems() throws Exception {
        ListViewNode list = (ListViewNode) UiSpecParser.parse(json("""
                {"type":"list","width":200,"height":100,"items":"{state.rows}","autoScroll":false,
                 "itemTemplate":{"type":"text","text":"{item.name}","visible":"{item.isUser}"}}
                """));
        StateAccess state = rootState(json("""
                {"rows":[{"name":"a","isUser":true},{"name":"b","isUser":false}]}
                """));

        list.measure(new MeasureContext(200, 100, 200, 100, TEXT, state));

        assertEquals(2, list.children().size());
        UiNode shown = list.children().get(0);
        UiNode hidden = list.children().get(1);
        assertTrue(shown.visibleNow());
        assertFalse(hidden.visibleNow());
        assertEquals(0f, hidden.height(), 0.001f);
    }

    private static StateAccess rootState(JsonObject root) {
        return new StateAccess() {
            @Override
            public String get(String path, String defaultValue) {
                JsonElement element = getElement(path);
                return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
            }

            @Override
            public JsonElement getElement(String path) {
                JsonElement current = root;
                for (String part : path.split("\\.")) {
                    if (current instanceof JsonObject object && object.has(part)) {
                        current = object.get(part);
                    } else {
                        return null;
                    }
                }
                return current;
            }
        };
    }

    /** 模拟 ListViewNode.ItemAccess 的条目上下文，验证 item 路径绑定。 */
    private static final class ItemState implements StateAccess {

        private final JsonObject item;
        private final int index;

        ItemState(JsonObject item, int index) {
            this.item = item;
            this.index = index;
        }

        @Override
        public String get(String path, String defaultValue) {
            if (path.equals("item")) {
                return item.toString();
            }
            if (path.equals("itemIndex")) {
                return Integer.toString(index);
            }
            if (path.equals("itemHighlight")) {
                return "false";
            }
            if (path.startsWith("item.")) {
                JsonElement element = elementAt(item, path.substring("item.".length()));
                if (element != null && element.isJsonPrimitive()) {
                    return element.getAsString();
                }
            }
            return defaultValue;
        }

        @Override
        public JsonElement getElement(String path) {
            if (path.equals("item")) {
                return item;
            }
            if (path.startsWith("item.")) {
                return elementAt(item, path.substring("item.".length()));
            }
            return null;
        }

        private static JsonElement elementAt(JsonElement root, String dotted) {
            JsonElement current = root;
            for (String part : dotted.split("\\.")) {
                if (current instanceof JsonObject object && object.has(part)) {
                    current = object.get(part);
                } else {
                    return null;
                }
            }
            return current;
        }
    }
}
