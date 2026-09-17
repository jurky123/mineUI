package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ColumnNode;
import com.mineui.ui.tree.RowNode;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSpecParserTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesColumnWithChildren() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                {
                  "type": "column",
                  "padding": 8,
                  "gap": 4,
                  "align": "center",
                  "children": [
                    { "type": "text", "text": "hi", "color": "#00E0FF" },
                    { "type": "button", "text": "go", "action": "go_action" }
                  ]
                }
                """));

        ColumnNode column = assertInstanceOf(ColumnNode.class, root);
        assertEquals(8f, column.style().padding().top(), 0.001f);
        assertEquals(4f, column.style().gap(), 0.001f);
        assertEquals(2, column.children().size());

        TextNode text = assertInstanceOf(TextNode.class, column.children().get(0));
        assertEquals("hi", text.template());
        assertEquals(0xFF00E0FF, text.color());

        ButtonNode button = assertInstanceOf(ButtonNode.class, column.children().get(1));
        assertEquals("go_action", button.action());
    }

    @Test
    void parsesRowAndNestedContainers() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "row", "gap": 2, "children": [
                    { "type": "column", "children": [ { "type": "text", "text": "a" } ] }
                ] }
                """));

        RowNode row = assertInstanceOf(RowNode.class, root);
        assertInstanceOf(ColumnNode.class, row.children().get(0));
    }

    @Test
    void parsesSizesAndColors() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "width": "50%", "height": 40, "background": "#80102030" }
                """));

        assertEquals(SizeSpec.Unit.PERCENT, root.style().width().unit());
        assertEquals(50f, root.style().width().value(), 0.001f);
        assertEquals(SizeSpec.Unit.PX, root.style().height().unit());
        assertEquals(0x80102030, root.style().background());
    }

    @Test
    void sixDigitColorGetsOpaqueAlpha() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "background": "#112233" }
                """));
        assertEquals(0xFF112233, root.style().background());
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json("""
                { "type": "webview" }
                """)));
    }

    @Test
    void rejectsChildrenOnLeafNode() {
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json("""
                { "type": "text", "text": "x", "children": [] }
                """)));
    }

    @Test
    void rejectsMissingType() {
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json("""
                { "width": 10 }
                """)));
    }

    @Test
    void rejectsInvalidColor() {
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json("""
                { "type": "box", "background": "not-a-color" }
                """)));
    }

    @Test
    void parsesActionOnAnyNode() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "width": 100, "height": 20, "action": "slot_0" }
                """));
        assertTrue(root.clickable());
        assertEquals("slot_0", root.action());
    }

    @Test
    void plainNodeIsNotClickable() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "width": 10, "height": 10 }
                """));
        assertFalse(root.clickable());
    }

    @Test
    void clickableContainerIsHitWhenChildIsNotInteractive() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "row", "width": 100, "height": 20, "action": "slot_0",
                  "children": [ { "type": "text", "text": "x" } ] }
                """));
        root.overrideWidth(100);
        root.overrideHeight(20);
        root.layout(0, 0);
        assertSame(root, root.mouseClicked(5, 5, 0));
    }

    @Test
    void parsesImageDefaults() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "image", "texture": "mineui:textures/gui/logo.png" }
                """));
        assertEquals("mineui:textures/gui/logo.png", ((com.mineui.ui.tree.ImageNode) root).texture());
        assertEquals(256f, ((com.mineui.ui.tree.ImageNode) root).textureWidth(), 0.001f);
    }

    @Test
    void rejectsTooDeeplyNestedDefinition() {
        int depth = UiSpecParser.MAX_DEPTH + 5;
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            nested.append("{\"type\":\"column\",\"children\":[");
        }
        nested.append("{\"type\":\"box\"}");
        for (int i = 0; i < depth; i++) {
            nested.append("]}");
        }
        String json = nested.toString();
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json(json)));
    }

    @Test
    void rejectsTooManyNodes() {
        StringBuilder nodes = new StringBuilder("{\"type\":\"column\",\"children\":[");
        for (int i = 0; i <= UiSpecParser.MAX_NODES; i++) {
            if (i > 0) {
                nodes.append(',');
            }
            nodes.append("{\"type\":\"box\",\"height\":1}");
        }
        nodes.append("]}");
        String json = nodes.toString();
        assertThrows(UiSpecException.class, () -> UiSpecParser.parse(json(json)));
    }
}
