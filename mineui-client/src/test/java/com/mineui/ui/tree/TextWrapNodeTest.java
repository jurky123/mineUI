package com.mineui.ui.tree;

import com.google.gson.JsonParser;
import com.mineui.ui.spec.UiSpecParser;
import com.mineui.ui.util.TextWrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextWrapNodeTest {

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

    private static MeasureContext ctx(float width, float height) {
        return new MeasureContext(width, height, width, height, TEXT, StateAccess.EMPTY);
    }

    private static TextNode parseText(String json) throws Exception {
        return (TextNode) UiSpecParser.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void wrappedTextHeightUsesLineCountAndConfiguredLineHeight() throws Exception {
        TextNode node = parseText("""
                {"type":"text","text":"abcdefghijklmnopqrstuvwxyz",
                 "wrap":true,"maxLines":3,"lineHeight":10,"width":"100%"}
                """);

        node.measure(ctx(120, 200));

        assertTrue(node.wrap());
        assertEquals(3, node.maxLines());
        assertEquals(10f, node.lineHeight(), 0.001f);
        assertEquals(10f, node.effectiveLineHeight(TEXT.lineHeight()), 0.001f);
        // 120px 宽 = 每行 20 字符；26 字符 → 2 行
        assertEquals(120f, node.width(), 0.001f);
        assertEquals(20f, node.height(), 0.001f);
        assertEquals(2, node.lines("abcdefghijklmnopqrstuvwxyz", 120, TEXT).size());
    }

    @Test
    void maxLinesCapsHeightAndEndsWithEllipsis() throws Exception {
        String longText = "a".repeat(70); // 每行 20 字符 → 4 行，被 maxLines=3 截断
        TextNode node = parseText("{\"type\":\"text\",\"text\":\"" + longText
                + "\",\"wrap\":true,\"maxLines\":3,\"lineHeight\":10,\"width\":\"100%\"}");

        node.measure(ctx(120, 200));

        List<String> lines = node.lines(longText, 120, TEXT);
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).endsWith(TextWrap.ELLIPSIS));
        assertTrue(TEXT.width(lines.get(2)) <= 120, "省略后的最后一行不能超过可用宽度");
        assertEquals(30f, node.height(), 0.001f);
    }

    @Test
    void wrapDefaultsOffAndKeepsSingleLineHeight() throws Exception {
        TextNode node = parseText("""
                {"type":"text","text":"abcdefghijklmnopqrstuvwxyz0123456789",
                 "width":"100%"}
                """);

        assertFalse(node.wrap());
        assertEquals(0, node.maxLines());

        node.measure(ctx(120, 200));

        assertEquals(120f, node.width(), 0.001f);
        assertEquals(9f, node.height(), 0.001f);
    }
}
