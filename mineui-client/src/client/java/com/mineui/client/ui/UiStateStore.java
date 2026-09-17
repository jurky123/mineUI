package com.mineui.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.json.JsonPatch;
import com.mineui.protocol.msg.PatchOp;
import com.mineui.ui.tree.StateAccess;

import java.util.List;

/**
 * 客户端界面状态（服务端权威，客户端只读渲染）。
 * 仅在渲染线程访问。
 */
public final class UiStateStore implements StateAccess {

    private JsonObject state = new JsonObject();
    private int session = -1;
    private int revision = -1;

    public void begin(int sessionId) {
        this.session = sessionId;
        this.revision = 0;
        this.state = new JsonObject();
    }

    public void end() {
        this.session = -1;
        this.revision = -1;
        this.state = new JsonObject();
    }

    public boolean active() {
        return session >= 0;
    }

    public int session() {
        return session;
    }

    public int revision() {
        return revision;
    }

    public void applySnapshot(int revision, JsonObject snapshot) {
        if (!active()) {
            return;
        }
        this.state = snapshot == null ? new JsonObject() : snapshot.deepCopy();
        this.revision = revision;
    }

    /** 修订号更旧的补丁直接忽略（TCP 有序，正常不会出现）。 */
    public void applyPatch(int revision, List<PatchOp> ops) throws ProtocolException {
        if (!active() || revision < this.revision) {
            return;
        }
        JsonPatch.apply(state, ops);
        this.revision = revision;
    }

    public String getString(String key, String defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : defaultValue;
    }

    /** 支持点分路径：{@code "player.name"}。 */
    @Override
    public String get(String path, String defaultValue) {
        JsonElement element = state;
        for (String part : path.split("\\.")) {
            if (element instanceof JsonObject object && object.has(part)) {
                element = object.get(part);
            } else {
                return defaultValue;
            }
        }
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }
}
