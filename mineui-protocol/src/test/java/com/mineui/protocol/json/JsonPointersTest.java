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

    @Test
    void encodeDecodePatchCarriesDeepCopiedValues() throws Exception {
        // 序列化往返：op value 必须是自包含快照，编码后解码再应用仍与服务端一致
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        com.google.gson.JsonElement next = JsonParser.parseString("\"Alice\"");
        JsonPointers.SetResult set = JsonPointers.set(server, "player.name", next);
        batch.add(JsonPointers.syncOp(set, JsonPointers.segments("player.name"), server, next));
        JsonPointers.set(server, "player.name", JsonParser.parseString("\"Bob\""));

        byte[] encoded = com.mineui.protocol.JsonCodec.encode(
                new com.mineui.protocol.msg.Patch(batch.drain()));
        com.mineui.protocol.msg.Patch decoded =
                com.mineui.protocol.JsonCodec.decode(encoded, com.mineui.protocol.msg.Patch.class);
        JsonObject client = new JsonObject();
        JsonPatch.apply(client, decoded.ops());
        // 客户端应用该 PATCH 后得到第一次写入时的分支快照，再应用后续增量即与服务端一致
        assertEquals(JsonParser.parseString("{\"player\":{\"name\":\"Alice\"}}"), client);
    }

    @Test
    void syncOpAddsTopmostCreatedBranch() throws Exception {
        JsonObject server = new JsonObject();
        JsonPointers.SetResult set = JsonPointers.set(server, "player.name",
                JsonParser.parseString("\"Alice\""));
        com.mineui.protocol.msg.PatchOp op = JsonPointers.syncOp(
                set, JsonPointers.segments("player.name"), server,
                JsonParser.parseString("\"Alice\""));
        assertEquals("add", op.op());
        assertEquals("/player", op.path());

        // 空客户端状态必须能直接应用
        JsonObject client = new JsonObject();
        JsonPatch.apply(client, java.util.List.of(op));
        assertEquals(server, client);
    }

    @Test
    void syncOpReplacesLeafWhenParentsExist() throws Exception {
        JsonObject server = JsonParser.parseString("{\"player\":{\"name\":\"Alice\"}}").getAsJsonObject();
        JsonPointers.SetResult set = JsonPointers.set(server, "player.name",
                JsonParser.parseString("\"Bob\""));
        com.mineui.protocol.msg.PatchOp op = JsonPointers.syncOp(
                set, JsonPointers.segments("player.name"), server,
                JsonParser.parseString("\"Bob\""));
        assertEquals("replace", op.op());
        assertEquals("/player/name", op.path());

        JsonObject client = JsonParser.parseString("{\"player\":{\"name\":\"Alice\"}}").getAsJsonObject();
        JsonPatch.apply(client, java.util.List.of(op));
        assertEquals(server, client);
    }

    @Test
    void syncOpBatchBranchThenLeafAppliesInOrder() throws Exception {
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        for (String[] write : new String[][]{{"player.name", "\"Alice\""}, {"player.name", "\"Bob\""}}) {
            JsonPointers.SetResult set = JsonPointers.set(server, write[0], JsonParser.parseString(write[1]));
            batch.add(JsonPointers.syncOp(set, JsonPointers.segments(write[0]), server,
                    JsonParser.parseString(write[1])));
        }
        java.util.List<com.mineui.protocol.msg.PatchOp> ops = batch.drain();
        // 第一次新建分支 add，第二次叶子 replace
        assertEquals("add", ops.get(0).op());
        assertEquals("/player", ops.get(0).path());
        assertEquals("replace", ops.get(1).op());

        JsonObject client = new JsonObject();
        JsonPatch.apply(client, ops);
        assertEquals(server, client);
    }

    @Test
    void syncOpSnapshotIsNotPollutedByLaterWrites() {
        // 快照隔离：syncOp 返回的分支 value 是深拷贝，后续写入同一子树不影响已生成的 op
        JsonObject server = new JsonObject();
        JsonPointers.SetResult set = JsonPointers.set(server, "player.name",
                JsonParser.parseString("\"Alice\""));
        com.mineui.protocol.msg.PatchOp op = JsonPointers.syncOp(
                set, JsonPointers.segments("player.name"), server,
                JsonParser.parseString("\"Alice\""));
        JsonPointers.set(server, "player.name", JsonParser.parseString("\"Bob\""));
        assertEquals("Alice", op.value().getAsJsonObject().get("name").getAsString());
        assertEquals("Bob", server.getAsJsonObject("player").get("name").getAsString());
    }
}
