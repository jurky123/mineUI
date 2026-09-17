package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiStyleParserTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesRadiusBorderShadowGradient() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                {
                  "type": "box",
                  "background": "#102030",
                  "radius": 8,
                  "border": { "color": "#3FA9F5", "width": 2 },
                  "shadow": { "color": "#000000", "size": 6, "offsetY": 3 },
                  "gradient": ["#102030", "#405060"]
                }
                """));

        assertEquals(8f, root.style().radius(), 0.001f);
        assertEquals(0xFF3FA9F5, root.style().borderColor());
        assertEquals(2f, root.style().borderWidth(), 0.001f);
        assertEquals(0xFF000000, root.style().shadowColor());
        assertEquals(6f, root.style().shadowSize(), 0.001f);
        assertEquals(3f, root.style().shadowOffsetY(), 0.001f);
        assertEquals(0xFF405060, root.style().gradientTo());
    }

    @Test
    void gradientWithoutBackgroundUsesFirstColor() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "gradient": ["#112233", "#445566"] }
                """));
        assertEquals(0xFF112233, root.style().background());
        assertEquals(0xFF445566, root.style().gradientTo());
    }

    @Test
    void defaultsForVisualFields() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box" }
                """));

        assertEquals(0f, root.style().radius(), 0.001f);
        assertNull(root.style().borderColor());
        assertNull(root.style().shadowColor());
        assertNull(root.style().gradientTo());
        assertEquals(0, root.style().z());
    }

    @Test
    void parsesZLayersClipAndPulse() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "column", "z": 3, "clip": true, "children": [
                    { "type": "text", "text": "x", "pulse": true }
                ] }
                """));

        assertEquals(3, root.style().z());
        assertTrue(root.style().clip());
        assertTrue(root.children().get(0).style().pulse());
    }

    @Test
    void collectsPulseNodesRecursively() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "column", "children": [
                    { "type": "text", "id": "a", "text": "x", "pulse": true },
                    { "type": "row", "children": [
                        { "type": "text", "id": "b", "text": "y", "pulse": true }
                    ] }
                ] }
                """));

        java.util.List<UiNode> pulses = new java.util.ArrayList<>();
        root.collectPulses(pulses);
        assertEquals(2, pulses.size());
        assertNotNull(root.findById("b"));
    }

    @Test
    void borderDefaultsToWidthOne() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "box", "border": { "color": "#FFFFFF" } }
                """));
        assertEquals(1f, root.style().borderWidth(), 0.001f);
    }
}
