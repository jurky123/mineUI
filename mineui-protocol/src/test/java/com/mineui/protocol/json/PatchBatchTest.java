package com.mineui.protocol.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.protocol.msg.PatchOp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PatchBatch} 回归测试：走真实的累积器 + {@link JsonPointers#syncOp}，
 * 再经 {@link JsonPatch} 应用，验证客户端最终状态与服务端一致。
 * <p>
 * 覆盖审查指出的父子路径混合 batch 场景：按序保留必然可应用，去重则会留下失效操作。
 */
class PatchBatchTest {

    /** 模拟 UiSession.state() 的写入路径：set → syncOp → batch 累积。 */
    private static void write(JsonObject server, PatchBatch batch, String key, String jsonValue) {
        com.google.gson.JsonElement next = JsonParser.parseString(jsonValue);
        JsonPointers.SetResult set = JsonPointers.set(server, key, next);
        batch.add(JsonPointers.syncOp(set, JsonPointers.segments(key), server, next));
    }

    @Test
    void parentOverwriteAfterChildAppliesInOrder() throws Exception {
        // 审查用例：先写子路径，再整体覆盖父对象；最终服务端期望 {"player":{}}
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        write(server, batch, "player.name", "\"Alice\"");
        write(server, batch, "player.name", "\"Bob\"");
        write(server, batch, "player", "{}");
        assertEquals(JsonParser.parseString("{\"player\":{}}"), server);

        List<PatchOp> ops = batch.drain();
        assertTrue(batch.isEmpty());
        JsonObject client = new JsonObject();
        JsonPatch.apply(client, ops);
        assertEquals(server, client);
    }

    @Test
    void childAfterParentOverwriteAppliesInOrder() throws Exception {
        // 反方向：先整体覆盖父对象，再写子路径；最终 {"player":{"name":"Bob"}}
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        write(server, batch, "player.name", "\"Alice\"");
        write(server, batch, "player", "{}");
        write(server, batch, "player.name", "\"Bob\"");
        assertEquals(JsonParser.parseString("{\"player\":{\"name\":\"Bob\"}}"), server);

        JsonObject client = new JsonObject();
        JsonPatch.apply(client, batch.drain());
        assertEquals(server, client);
    }

    @Test
    void earlierBranchOpIsSnapshotNotPollutedByLaterWrites() throws Exception {
        // 快照隔离：第一次 add /player 携带 {name:Alice}，后续改名不得污染已登记的 op；
        // 客户端按序应用后仍与服务端一致
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        write(server, batch, "player.name", "\"Alice\"");
        write(server, batch, "player.name", "\"Bob\"");

        JsonObject client = new JsonObject();
        JsonPatch.apply(client, batch.drain());
        assertEquals(server, client);
        assertEquals("Bob", client.getAsJsonObject("player").get("name").getAsString());
    }

    @Test
    void unrelatedFieldsStillBatchedInOnePatch() throws Exception {
        JsonObject server = new JsonObject();
        PatchBatch batch = new PatchBatch();
        write(server, batch, "title", "\"T\"");
        write(server, batch, "percent", "9");
        List<PatchOp> ops = batch.drain();
        assertTrue(batch.isEmpty());
        assertEquals(2, ops.size());
        JsonObject client = new JsonObject();
        JsonPatch.apply(client, ops);
        assertEquals(server, client);
    }
}
