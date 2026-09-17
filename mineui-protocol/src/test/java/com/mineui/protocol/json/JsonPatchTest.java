package com.mineui.protocol.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.PatchOp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPatchTest {

    private static JsonObject root() {
        JsonObject root = new JsonObject();
        root.addProperty("count", 1);
        JsonObject player = new JsonObject();
        player.addProperty("name", "Alice");
        root.add("player", player);
        JsonArray cards = new JsonArray();
        cards.add("red");
        cards.add("blue");
        root.add("cards", cards);
        return root;
    }

    @Test
    void replaceTopLevel() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.replace("/count", new JsonPrimitive(2))));
        assertEquals(2, root.get("count").getAsInt());
    }

    @Test
    void replaceNested() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.replace("/player/name", new JsonPrimitive("Bob"))));
        assertEquals("Bob", root.getAsJsonObject("player").get("name").getAsString());
    }

    @Test
    void addNewTopLevelKey() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.add("/title", new JsonPrimitive("Hello"))));
        assertEquals("Hello", root.get("title").getAsString());
    }

    @Test
    void addIntoArrayAtIndex() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.add("/cards/1", new JsonPrimitive("green"))));
        assertEquals(List.of("red", "green", "blue"),
                List.of(root.getAsJsonArray("cards").get(0).getAsString(),
                        root.getAsJsonArray("cards").get(1).getAsString(),
                        root.getAsJsonArray("cards").get(2).getAsString()));
    }

    @Test
    void addAppendWithDash() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.add("/cards/-", new JsonPrimitive("gold"))));
        assertEquals(3, root.getAsJsonArray("cards").size());
        assertEquals("gold", root.getAsJsonArray("cards").get(2).getAsString());
    }

    @Test
    void removeObjectKey() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.remove("/player")));
        assertFalse(root.has("player"));
    }

    @Test
    void removeArrayElement() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(PatchOp.remove("/cards/0")));
        assertEquals(1, root.getAsJsonArray("cards").size());
        assertEquals("blue", root.getAsJsonArray("cards").get(0).getAsString());
    }

    @Test
    void multipleOpsApplySequentially() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of(
                PatchOp.add("/title", new JsonPrimitive("T")),
                PatchOp.replace("/count", new JsonPrimitive(9)),
                PatchOp.remove("/player/name")));
        assertEquals("T", root.get("title").getAsString());
        assertEquals(9, root.get("count").getAsInt());
        assertFalse(root.getAsJsonObject("player").has("name"));
    }

    @Test
    void escapesTildeAndSlash() throws Exception {
        JsonObject root = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("a/b", 1);
        nested.addProperty("c~d", 2);
        root.add("weird", nested);

        JsonPatch.apply(root, List.of(
                PatchOp.replace("/weird/a~1b", new JsonPrimitive(10)),
                PatchOp.replace("/weird/c~0d", new JsonPrimitive(20))));
        assertEquals(10, root.getAsJsonObject("weird").get("a/b").getAsInt());
        assertEquals(20, root.getAsJsonObject("weird").get("c~d").getAsInt());
    }

    @Test
    void rejectsInvalidPointer() {
        JsonObject root = root();
        assertThrows(ProtocolException.class,
                () -> JsonPatch.apply(root, List.of(PatchOp.add("count", new JsonPrimitive(1)))));
    }

    @Test
    void rejectsReplaceMissingTarget() {
        JsonObject root = root();
        assertThrows(ProtocolException.class,
                () -> JsonPatch.apply(root, List.of(PatchOp.replace("/missing", new JsonPrimitive(1)))));
    }

    @Test
    void rejectsRemoveMissingTarget() {
        JsonObject root = root();
        assertThrows(ProtocolException.class, () -> JsonPatch.apply(root, List.of(PatchOp.remove("/missing"))));
    }

    @Test
    void rejectsArrayIndexOutOfBounds() {
        JsonObject root = root();
        assertThrows(ProtocolException.class,
                () -> JsonPatch.apply(root, List.of(PatchOp.replace("/cards/5", new JsonPrimitive("x")))));
    }

    @Test
    void rejectsUnknownOp() {
        JsonObject root = root();
        assertThrows(ProtocolException.class,
                () -> JsonPatch.apply(root, List.of(new PatchOp("move", "/count", new JsonPrimitive(1)))));
    }

    @Test
    void rejectsMissingValueForReplace() {
        JsonObject root = root();
        assertThrows(ProtocolException.class,
                () -> JsonPatch.apply(root, List.of(new PatchOp("replace", "/count", null))));
    }

    @Test
    void emptyOpsIsNoop() throws Exception {
        JsonObject root = root();
        JsonPatch.apply(root, List.of());
        assertTrue(root.has("count"));
    }
}
