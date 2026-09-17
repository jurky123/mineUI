package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewNodeParserTest {

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

    private static MeasureContext ctx() {
        return new MeasureContext(400, 400, 400, 400, TEXT, StateAccess.EMPTY);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesItemNode() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                { "type": "item", "item": "minecraft:diamond", "count": 3, "model": 12345,
                  "scale": 2.0, "itemTooltip": true, "fake": true }
                """));

        ItemViewNode item = assertInstanceOf(ItemViewNode.class, root);
        assertEquals("minecraft:diamond", item.item());
        assertEquals(3, item.count());
        assertEquals(12345, item.model());
        assertEquals(2f, item.scale(), 0.001f);
        assertTrue(item.itemTooltip());
        assertTrue(item.fake());
        assertTrue(item.hasTooltip());

        item.measure(ctx());
        assertEquals(32, item.width(), 0.01f);
        assertEquals(32, item.height(), 0.01f);
    }

    @Test
    void itemDefaults() throws Exception {
        ItemViewNode item = assertInstanceOf(ItemViewNode.class, UiSpecParser.parse(json("""
                { "type": "item" }
                """)));
        assertEquals("minecraft:paper", item.item());
        assertEquals(1, item.count());
        assertEquals(-1, item.model());
        assertEquals(List.of(), item.modelStrings());
        assertFalse(item.hasTooltip());
        item.measure(ctx());
        assertEquals(16, item.width(), 0.01f);
    }

    @Test
    void parsesModelStrings() throws Exception {
        ItemViewNode item = assertInstanceOf(ItemViewNode.class, UiSpecParser.parse(json("""
                { "type": "item", "item": "minecraft:paper", "modelStrings": ["red_5", "wild4"] }
                """)));
        assertEquals(List.of("red_5", "wild4"), item.modelStrings());
    }

    @Test
    void parsesEntityNodeWithDefaults() throws Exception {
        EntityViewNode entity = assertInstanceOf(EntityViewNode.class, UiSpecParser.parse(json("""
                { "type": "entity", "entity": "minecraft:zombie", "yaw": 90 }
                """)));
        assertEquals("minecraft:zombie", entity.entityType());
        assertEquals(30f, entity.scale(), 0.001f);
        assertTrue(entity.followMouse());
        assertEquals(90f, entity.yaw(), 0.001f);
        assertEquals(90f, entity.bodyYaw(), 0.001f);
        assertEquals(0f, entity.pitch(), 0.001f);

        entity.measure(ctx());
        assertEquals(64, entity.width(), 0.01f);
        assertEquals(96, entity.height(), 0.01f);
    }

    @Test
    void parsesPlayerNode() throws Exception {
        PlayerViewNode player = assertInstanceOf(PlayerViewNode.class, UiSpecParser.parse(json("""
                { "type": "player", "player": "Oathtory", "width": 80, "height": 120, "scale": 24, "followMouse": false }
                """)));
        assertEquals("Oathtory", player.player());
        assertEquals(24f, player.scale(), 0.001f);
        assertFalse(player.followMouse());

        player.measure(ctx());
        assertEquals(80, player.width(), 0.01f);
        assertEquals(120, player.height(), 0.01f);
    }

    @Test
    void playerDefaultsToSelf() throws Exception {
        PlayerViewNode player = assertInstanceOf(PlayerViewNode.class, UiSpecParser.parse(json("""
                { "type": "player" }
                """)));
        assertEquals("@self", player.player());
        assertTrue(player.followMouse());
    }

    @Test
    void entityRequiresEntityField() {
        try {
            UiSpecParser.parse(json("""
                    { "type": "entity" }
                    """));
            throw new AssertionError("应当抛出 UiSpecException");
        } catch (UiSpecException expected) {
            // ok
        }
    }

    @Test
    void previewNodesRespectInvisible() throws Exception {
        ItemViewNode item = assertInstanceOf(ItemViewNode.class, UiSpecParser.parse(json("""
                { "type": "item", "visible": false }
                """)));
        item.measure(new MeasureContext(400, 400, 400, 400, TEXT,
                (path, fallback) -> fallback));
        assertFalse(item.visibleNow());
        assertEquals(0, item.width(), 0.01f);
    }

    @Test
    void nodeStyleDefaultsUnused() {
        NodeStyle style = NodeStyle.defaults();
        assertEquals(0f, style.radius(), 0.001f);
    }
}
