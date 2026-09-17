package com.mineui.ui.spec;

import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 守护内置皮肤浏览器页面：JSON 可解析、可布局、关键绑定可用。 */
class SkinBrowserDefinitionTest {

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

    @Test
    void parsesMeasuresAndBinds() throws Exception {
        UiDefinition definition = UiDefinitionLoader.load("skin", "browser", Path.of("/nonexistent-dev-root"));

        Map<String, String> values = new HashMap<>();
        values.put("title", "皮肤浏览器");
        values.put("subtitle", "共 94 款");
        values.put("page", "1 / 12");
        values.put("status", "点击左侧皮肤");
        values.put("footer", "数据：SkinsRestorer");
        values.put("preview.name", "Fox");
        values.put("preview.value", "dGVzdA==");
        values.put("preview.signature", "c2ln");
        for (int i = 0; i < 8; i++) {
            values.put("slots." + i + ".name", "Skin " + i);
            values.put("slots." + i + ".tooltip", "Skin " + i + " · /skin skin-" + i);
            values.put("slots." + i + ".selected", i == 0 ? "true" : "false");
        }
        StateAccess state = (path, fallback) -> values.getOrDefault(path, fallback);

        MeasureContext context = new MeasureContext(427, 320, 427, 320, TEXT, state);
        definition.root().measure(context);
        definition.root().layout(0, 0);

        PlayerViewNode preview = assertInstanceOf(PlayerViewNode.class, definition.root().findById("preview"));
        assertTrue(preview.hasSkin());
        assertTrue(preview.width() > 0);
        assertTrue(preview.height() > 0);

        assertEquals(8, countSlots(definition.root()));
        assertEquals("皮肤浏览器", Bindings.resolve("{state.title}", state));
        assertEquals("Fox", Bindings.resolve("{state.preview.name}", state));
    }

    private static int countSlots(UiNode node) {
        int count = node.action().startsWith("slot_") ? 1 : 0;
        for (UiNode child : node.children()) {
            count += countSlots(child);
        }
        return count;
    }
}
