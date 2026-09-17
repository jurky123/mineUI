package com.mineui.api;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * 一次 MineUI 界面会话（服务端权威状态）。
 * <p>
 * 生命周期：{@code open() → state()/snapshot() → (action/patch)* → close()}。
 * 除 {@link #closed()} 外所有方法必须在主线程调用。
 */
public interface MineUiSession {

    /** 会话玩家。 */
    Player player();

    /** 设置顶层状态字段；snapshot 之后会立即下发 PATCH。 */
    MineUiSession state(String key, Object value);

    /** 注册动作处理器。 */
    MineUiSession on(String actionId, Consumer<MineUiAction> handler);

    /** 下发完整状态，并开始按修订号增量同步。 */
    void snapshot();

    /** 关闭会话并通知客户端。 */
    void close();

    boolean closed();
}
