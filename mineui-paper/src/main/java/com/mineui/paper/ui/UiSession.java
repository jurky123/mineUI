package com.mineui.paper.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.api.MineUiAction;
import com.mineui.api.MineUiSession;
import com.mineui.paper.MineUiPlugin;
import com.mineui.paper.Transport;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.json.JsonPatch;
import com.mineui.protocol.json.JsonPointers;
import com.mineui.protocol.msg.Patch;
import com.mineui.protocol.msg.PatchOp;
import com.mineui.protocol.msg.Snapshot;
import com.mineui.protocol.session.RateWindow;
import com.mineui.protocol.session.RevisionGuard;
import com.mineui.protocol.msg.Action;
import com.mineui.protocol.msg.Open;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 一次界面会话（服务端权威状态）。
 * <p>
 * 生命周期：{@code open() → state()/snapshot() → (action/patch)* → close()}。
 * 除 {@link #closed()} 外，所有方法必须在主线程调用。
 */
public final class UiSession implements MineUiSession {

    public static final String MODE_SCREEN = "screen";
    public static final String MODE_HUD = "hud";

    /** 每秒最多接受的动作数（防连点/恶意刷包）。 */
    private static final int MAX_ACTIONS_PER_SECOND = 8;
    private static final long RATE_WINDOW_MILLIS = 1000L;

    private final MineUiPlugin plugin;
    private final Plugin owner;
    private final Player player;
    private final int id;
    private final String app;
    private final String view;
    private final JsonObject definition;
    private final String mode;
    private final com.mineui.protocol.msg.HudLayout layout;
    private final JsonObject state = new JsonObject();
    private final RevisionGuard revisionGuard = new RevisionGuard();
    private final Map<String, Consumer<MineUiAction>> handlers = new HashMap<>();
    private final java.util.List<Runnable> closeHooks = new java.util.ArrayList<>();
    private final RateWindow actionRate = new RateWindow(MAX_ACTIONS_PER_SECOND, RATE_WINDOW_MILLIS);

    private boolean snapshotSent;
    private volatile boolean closed;
    private int patchSeq;
    /** 批量更新中：state() 只记录待发 op，由 batch() 结束时合并成一个 PATCH。 */
    private boolean batching;
    /** 待发 op（按 pointer 去重，保留最后一次写入）。 */
    private final Map<String, PatchOp> pendingOps = new java.util.LinkedHashMap<>();

    UiSession(MineUiPlugin plugin, Plugin owner, Player player, int id, String app, String view,
              JsonObject definition, String mode, com.mineui.protocol.msg.HudLayout layout) {
        this.plugin = plugin;
        this.owner = owner;
        this.player = player;
        this.id = id;
        this.app = app;
        this.view = view;
        this.definition = definition == null ? null : definition.deepCopy();
        this.mode = mode == null ? MODE_SCREEN : mode;
        this.layout = layout;
    }

    // ---------- 对外 API（业务插件使用） ----------

    /**
     * 发送 OPEN（携带业务插件自带的界面定义；null 表示由客户端内置/开发目录加载）。
     * 业务代码应先调用本方法，再设置初始 state，最后调用 {@link #snapshot()}。
     */
    public void open() {
        requireOpen();
        // 握手期的策略包可能因客户端仍在加载而丢失：每次开会话随 OPEN 前补发（幂等，保证渲染前策略就绪）
        plugin.transport().sendNow(player, new Envelope(MessageType.REMOTE_POLICY, 0, 0,
                JsonCodec.encode(plugin.remoteImagePolicy())));
        send(MessageType.OPEN, 0, JsonCodec.encode(new Open(app, view, definition, mode, layout)));
    }

    /**
     * 设置状态字段；snapshot 之后会立即下发 PATCH。
     * <p>
     * {@code key} 支持点分嵌套路径（如 {@code "player.name"}），与读取侧
     * {@code {state.player.name}} 绑定保持一致；中间缺失对象自动创建。
     * 值未变化时不发 PATCH；处于 {@link #batch} 中时只登记待发 op，由批结束时合并成一个 PATCH。
     */
    @Override
    public UiSession state(String key, Object value) {
        JsonElement next = JsonCodec.toJsonTree(value);
        JsonPointers.SetResult set = JsonPointers.set(state, key, next);
        if (!snapshotSent || closed) {
            return this;
        }
        if (next.equals(set.previous())) {
            // 相等值去重：本地已拥有该字段时，业务每秒重复 push 不再产生网络包
            return this;
        }
        PatchOp op = set.existed()
                ? PatchOp.replace(set.pointer(), next)
                : PatchOp.add(set.pointer(), next);
        if (batching) {
            PatchOp first = pendingOps.get(set.pointer());
            if (first != null) {
                // 同一批内重复写入：保留第一次的操作类型（由批次开始时客户端的真实状态决定），只更新值。
                // 否则 add 会被后来的 replace 覆盖，客户端因缺少该路径而无法应用 PATCH。
                pendingOps.put(set.pointer(), new PatchOp(first.op(), first.path(), next));
            } else {
                pendingOps.put(set.pointer(), op);
            }
        } else {
            sendPatch(List.of(op));
        }
        return this;
    }

    /**
     * 批量更新：期间多次 {@link #state} 只在结束时合并成一个 PATCH（按字段去重，保留最后一次）。
     * <pre>{@code
     * session.batch(() -> {
     *     session.state("title", t);
     *     session.state("percent", p);
     * });
     * }</pre>
     */
    @Override
    public UiSession batch(Runnable updates) {
        if (updates == null) {
            return this;
        }
        if (batching) {
            // 嵌套批量：并入外层，由最外层统一发送
            updates.run();
            return this;
        }
        batching = true;
        try {
            updates.run();
        } finally {
            batching = false;
            if (!pendingOps.isEmpty() && !closed) {
                List<PatchOp> ops = new java.util.ArrayList<>(pendingOps.values());
                pendingOps.clear();
                sendPatch(ops);
            } else {
                pendingOps.clear();
            }
        }
        return this;
    }

    /** 会话所有者插件（可为 MineUI 自身）。 */
    public Plugin owner() {
        return owner;
    }

    /** 下发完整状态，并开始按修订号增量同步。 */
    @Override
    public void snapshot() {
        requireOpen();
        snapshotSent = true;
        send(MessageType.SNAPSHOT, revisionGuard.revision(), JsonCodec.encode(new Snapshot(state)));
    }

    /** 注册动作处理器。 */
    @Override
    public UiSession on(String actionId, Consumer<MineUiAction> handler) {
        handlers.put(actionId, handler);
        return this;
    }

    /** 关闭会话：下发 CLOSE 并从管理器移除。 */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        handlers.clear();
        runCloseHooks();
        plugin.uiSessions().remove(this);
        if (player.isOnline()) {
            send(MessageType.CLOSE, revisionGuard.revision(), new byte[0]);
        }
    }

    /** 该包的目标会话是否就是本会话（用于拒绝旧页面的延迟包）。 */
    public boolean matches(int sessionId) {
        return !closed && sessionId == id;
    }

    public int id() {
        return id;
    }

    public java.util.UUID playerId() {
        return player.getUniqueId();
    }

    public Player player() {
        return player;
    }

    public String app() {
        return app;
    }

    public String view() {
        return view;
    }

    /** {@code "screen"} 或 {@code "hud"}。 */
    public String mode() {
        return mode;
    }

    public boolean closed() {
        return closed;
    }

    public String getString(String key, String defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonElement element = state.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : defaultValue;
    }

    // ---------- 内部：网络事件 ----------

    void handleAction(int incomingRevision, Action action) {
        if (closed) {
            return;
        }
        if (!rateLimitAllows()) {
            plugin.getLogger().fine(() -> player.getName() + " MineUI 动作超过限速，已丢弃: " + action.id());
            return;
        }
        switch (revisionGuard.submit(incomingRevision)) {
            case ACCEPT -> {
                Consumer<MineUiAction> handler = handlers.get(action.id());
                if (handler == null) {
                    plugin.getLogger().fine(() -> "未注册的 MineUI 动作: " + action.id() + " (" + app + "/" + view + ")");
                } else {
                    int before = patchSeq;
                    // 把一次动作的状态修改合并成一个 PATCH（业务可继续自行 batch，嵌套时并入）
                    batch(() -> {
                        try {
                            handler.accept(new ActionEvent(player, action.id(), action.payload()));
                        } catch (Exception e) {
                            // 业务处理器异常隔离：不阻断修订号同步，也不影响其他会话/动作
                            plugin.getLogger().warning("MineUI 动作处理器异常 " + app + "/" + view
                                    + " #" + action.id() + "（" + player.getName() + "）: " + e);
                        }
                    });
                    if (patchSeq == before && !closed) {
                        // 处理器没有改变状态：发空 PATCH 同步修订号，避免下一次动作被判过期
                        sendPatch(List.of());
                    }
                }
            }
            case STALE, INVALID -> {
                plugin.getLogger().fine(() -> player.getName() + " MineUI 修订号不同步 (收到 "
                        + incomingRevision + ", 当前 " + revisionGuard.revision() + ")，重新下发快照");
                resync();
            }
        }
    }

    void handleClientClose(int sessionId) {
        if (sessionId == id) {
            close();
        }
    }

    /** 玩家退出等场景：不发送 CLOSE，直接作废并释放订阅。 */
    void discard() {
        closed = true;
        handlers.clear();
        runCloseHooks();
    }

    @Override
    public void onClose(Runnable callback) {
        if (callback != null) {
            closeHooks.add(callback);
        }
    }

    private void runCloseHooks() {
        for (Runnable hook : closeHooks) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("MineUI 关闭回调异常: " + e.getMessage());
            }
        }
        closeHooks.clear();
    }

    void resync() {
        if (snapshotSent && !closed) {
            send(MessageType.SNAPSHOT, revisionGuard.revision(), JsonCodec.encode(new Snapshot(state)));
        }
    }

    // ---------- 私有 ----------

    private void sendPatch(List<PatchOp> ops) {
        patchSeq++;
        send(MessageType.PATCH, revisionGuard.revision(), JsonCodec.encode(new Patch(ops)));
    }

    private void send(MessageType type, int revision, byte[] payload) {
        // 双端上限：客户端 Envelope 解码同样按此上限校验；这里给出可定位的业务报错
        if (payload.length > Envelope.MAX_PAYLOAD_SIZE) {
            throw new IllegalStateException("MineUI " + type + " 载荷过大: " + payload.length
                    + "B（上限 " + Envelope.MAX_PAYLOAD_SIZE + "B），请缩减 " + app + "/" + view
                    + " 的界面定义或状态字段");
        }
        Transport transport = plugin.transport();
        transport.sendNow(player, new Envelope(type, id, revision, payload));
    }

    private boolean rateLimitAllows() {
        return actionRate.tryAcquire();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("会话已关闭: " + id);
        }
    }
}
