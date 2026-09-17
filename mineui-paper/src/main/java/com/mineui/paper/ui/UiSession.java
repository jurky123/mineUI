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

    /** 每秒最多接受的动作数（防连点/恶意刷包）。 */
    private static final int MAX_ACTIONS_PER_SECOND = 8;
    private static final long RATE_WINDOW_MILLIS = 1000L;

    private final MineUiPlugin plugin;
    private final Plugin owner;
    private final Player player;
    private final int id;
    private final String app;
    private final String view;
    private final JsonObject state = new JsonObject();
    private final RevisionGuard revisionGuard = new RevisionGuard();
    private final Map<String, Consumer<MineUiAction>> handlers = new HashMap<>();
    private final RateWindow actionRate = new RateWindow(MAX_ACTIONS_PER_SECOND, RATE_WINDOW_MILLIS);

    private boolean snapshotSent;
    private volatile boolean closed;
    private int patchSeq;

    UiSession(MineUiPlugin plugin, Plugin owner, Player player, int id, String app, String view) {
        this.plugin = plugin;
        this.owner = owner;
        this.player = player;
        this.id = id;
        this.app = app;
        this.view = view;
    }

    // ---------- 对外 API（业务插件使用） ----------

    /** 发送 OPEN。业务代码应先调用本方法，再设置初始 state，最后调用 {@link #snapshot()}。 */
    public void open() {
        requireOpen();
        send(MessageType.OPEN, 0, JsonCodec.encode(new Open(app, view)));
    }

    /** 设置顶层状态字段；snapshot 之后会立即下发 PATCH。 */
    @Override
    public UiSession state(String key, Object value) {
        boolean existed = state.has(key);
        state.add(key, JsonCodec.toJsonTree(value));
        if (snapshotSent && !closed) {
            JsonElement element = state.get(key);
            PatchOp op = existed ? PatchOp.replace(pointer(key), element) : PatchOp.add(pointer(key), element);
            sendPatch(List.of(op));
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
                    handler.accept(new ActionEvent(player, action.id(), action.payload()));
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
        Transport transport = plugin.transport();
        transport.sendNow(player, new Envelope(type, id, revision, payload));
    }

    private boolean rateLimitAllows() {
        return actionRate.tryAcquire();
    }

    private static String pointer(String key) {
        return "/" + key.replace("~", "~0").replace("/", "~1");
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("会话已关闭: " + id);
        }
    }
}
