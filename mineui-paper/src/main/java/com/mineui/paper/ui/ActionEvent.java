package com.mineui.paper.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.api.MineUiAction;
import org.bukkit.entity.Player;

/** 业务插件收到的用户操作事件。 */
public final class ActionEvent implements MineUiAction {

    private final Player player;
    private final String id;
    private final JsonObject payload;

    public ActionEvent(Player player, String id, JsonObject payload) {
        this.player = player;
        this.id = id;
        this.payload = payload;
    }

    public Player player() {
        return player;
    }

    public String id() {
        return id;
    }

    public JsonObject payload() {
        return payload;
    }

    public String getString(String key, String defaultValue) {
        JsonElement element = payload.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        JsonElement element = payload.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonElement element = payload.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : defaultValue;
    }
}
