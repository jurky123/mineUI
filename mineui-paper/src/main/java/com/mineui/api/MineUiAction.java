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
}
