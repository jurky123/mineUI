package com.mineui.api;

import com.google.gson.JsonElement;
import org.bukkit.entity.Player;

/** 业务插件收到的用户操作事件。 */
public interface MineUiAction {

    Player player();

    /** 动作 id（对应客户端节点的 {@code action} 字段）。 */
    String id();

    /** 动作载荷（无载荷时为空对象）。 */
    JsonElement payload();

    /** 读取载荷中的字符串字段（无则返回默认值），输入框提交等场景使用。 */
    default String string(String key, String fallback) {
        JsonElement payload = payload();
        if (payload != null && payload.isJsonObject()) {
            JsonElement value = payload.getAsJsonObject().get(key);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return fallback;
    }

    /** 读取载荷中的数值字段（无则返回默认值），滑块提交等场景使用。 */
    default double number(String key, double fallback) {
        JsonElement payload = payload();
        if (payload != null && payload.isJsonObject()) {
            JsonElement value = payload.getAsJsonObject().get(key);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsDouble();
            }
        }
        return fallback;
    }
}
