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

    /**
     * 设置状态字段；snapshot 之后会立即下发 PATCH。
     * <p>
     * {@code key} 支持点分嵌套路径（如 {@code "player.name"}），与读取侧
     * {@code {state.player.name}} 绑定一致；中间缺失的对象自动创建，
     * 中间段已是标量/数组时抛 {@link IllegalArgumentException}。
     * 值未变化时不发送；相同字段重复设置只保留最后一次。
     */
    MineUiSession state(String key, Object value);

    /**
     * 批量更新：期间多次 {@link #state} 合并成一个 PATCH 发送，减少高频刷新时的包数。
     * <pre>{@code
     * session.batch(() -> {
     *     session.state("title", t);
     *     session.state("percent", p);
     * });
     * }</pre>
     */
    MineUiSession batch(Runnable updates);

    /** 注册动作处理器。 */
    MineUiSession on(String actionId, Consumer<MineUiAction> handler);

    /**
     * 注册关闭回调（close/discard/owner 停用均会触发一次）。
     * 用于取消业务侧的定时任务、订阅等资源。
     */
    void onClose(Runnable callback);

    /** 下发完整状态，并开始按修订号增量同步。 */
    void snapshot();

    /** 关闭会话并通知客户端。 */
    void close();

    boolean closed();
}
