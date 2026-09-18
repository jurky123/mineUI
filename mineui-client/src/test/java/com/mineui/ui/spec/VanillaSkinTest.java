package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.InputNode;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.SliderNode;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class VanillaSkinTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void buttonSkinFillsVanillaSprites() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "button", "skin": "vanilla:button", "text": "确定" }
                """));

        ButtonNode button = assertInstanceOf(ButtonNode.class, root);
        NodeStyle style = button.style();
        assertEquals("minecraft:widget/button", style.sprite());
        assertEquals("minecraft:widget/button_highlighted", style.spriteHover());
        assertEquals(20f, style.height().value(), 0.001f);
    }

    @Test
    void explicitFieldsOverrideSkin() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "button", "skin": "vanilla:button", "text": "x", "height": 30, "color": "#FF0000" }
                """));

        ButtonNode button = assertInstanceOf(ButtonNode.class, root);
        assertEquals(30f, button.style().height().value(), 0.001f);
        assertEquals(0xFFFF0000, button.textColor());
    }

    @Test
    void inputSkinFocusSprite() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "input", "skin": "vanilla:input", "placeholder": "搜索" }
                """));

        InputNode input = assertInstanceOf(InputNode.class, root);
        assertEquals("minecraft:widget/text_field", input.style().sprite());
        assertEquals("minecraft:widget/text_field_highlighted", input.style().spriteFocus());
        assertEquals(0xFF707070, input.placeholderColor());
    }

    @Test
    void vanillaShorthandInfersByType() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "slider", "skin": "vanilla", "min": 0, "max": 10 }
                """));

        SliderNode slider = assertInstanceOf(SliderNode.class, root);
        assertEquals(20f, slider.style().height().value(), 0.001f);
        assertEquals(10, slider.max(), 0.001);
    }

    @Test
    void glassSkinIsBorderlessTranslucent() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "column", "skin": "mineui:glass", "children": [] }
                """));
        assertEquals(0x8A0E141B, root.style().background());
        assertNull(root.style().borderColor());
        assertEquals(6f, root.style().radius(), 0.001f);
        assertEquals(8f, root.style().padding().top(), 0.001f);
    }

    @Test
    void glassShorthandWithoutNamespaceWorks() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "column", "skin": "glass", "children": [] }
                """));
        assertEquals(0x8A0E141B, root.style().background());
    }

    @Test
    void unknownSkinIsIgnored() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "button", "skin": "vanilla:unknown", "text": "x" }
                """));
        assertNull(root.style().sprite());
    }
}
