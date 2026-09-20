package com.mineui.protocol.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPointersTest {

    @Test
    void topLevelFieldKeepsOldSemantics() {
        JsonObject root = new JsonObject();
        JsonPointers.SetResult first = JsonPointers.set(root, "title", JsonParser.parseString("\"a\""));
        assertFalse(first.existed());
        assertEquals("/title", first.pointer());
        assertNull(first.previous());

        JsonPointers.SetResult second = JsonPointers.set(root, "title", JsonParser.parseString("\"b\""));
        assertTrue(second.existed());
        assertEquals("/title", second.pointer());
        assertEquals("a", second.previous().getAsString());
    }

    @Test
    void nestedPathCreatesIntermediateObjects() {
        JsonObject root = new JsonObject();
        JsonPointers.SetResult result = JsonPointers.set(root, "player.name", JsonParser.parseString("\"a\""));
        assertFalse(result.existed());
        assertEquals("/player/name", result.pointer());
        assertEquals("a", JsonPointers.get(root, "player.name").getAsString());
    }

    @Test
    void nestedLeafExistenceTracked() {
        JsonObject root = new JsonObject();
        JsonPointers.set(root, "a.b", JsonParser.parseString("1"));
        JsonPointers.SetResult again = JsonPointers.set(root, "a.b", JsonParser.parseString("2"));
        assertTrue(again.existed());
        assertEquals(1, again.previous().getAsInt());
    }

    @Test
    void scalarIntermediateIsConflict() {
        JsonObject root = new JsonObject();
        root.addProperty("a", 1);
        assertThrows(IllegalArgumentException.class,
                () -> JsonPointers.set(root, "a.b", JsonParser.parseString("2")));
    }

    @Test
    void emptySegmentsRejected() {
        JsonObject root = new JsonObject();
        assertThrows(IllegalArgumentException.class, () -> JsonPointers.set(root, "", JsonParser.parseString("1")));
        assertThrows(IllegalArgumentException.class, () -> JsonPointers.set(root, "a..b", JsonParser.parseString("1")));
        assertThrows(IllegalArgumentException.class, () -> JsonPointers.set(root, ".a", JsonParser.parseString("1")));
    }

    @Test
    void pointerEscapesSpecialChars() {
        JsonObject root = new JsonObject();
        JsonPointers.SetResult result = JsonPointers.set(root, "a~/b", JsonParser.parseString("1"));
        assertEquals("/a~0~1b", result.pointer());
    }

    @Test
    void getMissingReturnsNull() {
        JsonObject root = new JsonObject();
        assertNull(JsonPointers.get(root, "x.y"));
        JsonPointers.set(root, "x", JsonParser.parseString("1"));
        assertNull(JsonPointers.get(root, "x.y"));
    }
}
