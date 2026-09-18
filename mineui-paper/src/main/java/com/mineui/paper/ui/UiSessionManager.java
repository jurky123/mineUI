package com.mineui.paper.ui;

import com.google.gson.JsonObject;
import com.mineui.paper.MineUiPlugin;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Action;
import com.mineui.protocol.msg.HudLayout;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线界面会话管理：每个玩家可同时拥有一个屏幕会话 + 若干 HUD 会话。
 * <p>
 * 会话的创建/关闭/动作分发均在主线程执行（由插件消息回调调度过来）。
 */
public final class UiSessionManager {

    private final MineUiPlugin plugin;
    private final Map<UUID, Map<Integer, UiSession>> active = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public UiSessionManager(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 打开 ----------

    /** 打开屏幕会话（替换该玩家已有屏幕会话，保留 HUD）。 */
    public UiSession open(Player player, String app, String view) {
        return open(plugin, player, app, view, null);
    }

    public UiSession open(Plugin owner, Player player, String app, String view) {
        return open(owner, player, app, view, null);
    }

    public UiSession open(Plugin owner, Player player, String app, String view, JsonObject definition) {
        return open(owner, player, app, view, definition, UiSession.MODE_SCREEN, null);
    }

    /**
     * 打开 HUD 会话（同 app/view 的旧 HUD 会被替换；屏幕与其他 HUD 不受影响）。
     *
     * @param definition 界面定义 JSON（null 表示由客户端内置/开发目录加载）
     * @param layout     HUD 布局（null 用默认值）
     */
    public UiSession openHud(Plugin owner, Player player, String app, String view,
                             JsonObject definition, HudLayout layout) {
        return open(owner, player, app, view, definition, UiSession.MODE_HUD, layout);
    }

    private UiSession open(Plugin owner, Player player, String app, String view,
                           JsonObject definition, String mode, HudLayout layout) {
        boolean openingScreen = UiSession.MODE_SCREEN.equals(mode);
        for (UiSession existing : playerSessions(player)) {
            boolean existingScreen = UiSession.MODE_SCREEN.equals(existing.mode());
            boolean existingHud = UiSession.MODE_HUD.equals(existing.mode());
            boolean sameView = existing.app().equals(app) && existing.view().equals(view);
            // 开屏幕：替换旧屏幕；开 HUD：只替换同 app/view 的旧 HUD
            if (openingScreen ? existingScreen : (existingHud && sameView)) {
                existing.close();
            }
        }
        UiSession session = new UiSession(plugin, owner, player, nextId.getAndIncrement(),
                app, view, definition, mode, layout);
        active.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(session.id(), session);
        session.open();
        return session;
    }

    // ---------- 查询 ----------

    /** 玩家的屏幕会话（可能为 null）。 */
    public UiSession getScreen(Player player) {
        for (UiSession session : playerSessions(player)) {
            if (UiSession.MODE_SCREEN.equals(session.mode())) {
                return session;
            }
        }
        return null;
    }

    /** 玩家的 HUD 会话列表（快照）。 */
    public List<UiSession> getHuds(Player player) {
        List<UiSession> result = new ArrayList<>();
        for (UiSession session : playerSessions(player)) {
            if (UiSession.MODE_HUD.equals(session.mode())) {
                result.add(session);
            }
        }
        return result;
    }

    /** 玩家全部会话（快照）。 */
    public List<UiSession> getSessions(Player player) {
        return new ArrayList<>(playerSessions(player));
    }

    public int count() {
        int total = 0;
        for (Map<Integer, UiSession> sessions : active.values()) {
            total += sessions.size();
        }
        return total;
    }

    public int countHuds() {
        int total = 0;
        for (Map<Integer, UiSession> sessions : active.values()) {
            for (UiSession session : sessions.values()) {
                if (UiSession.MODE_HUD.equals(session.mode())) {
                    total++;
                }
            }
        }
        return total;
    }

    // ---------- 网络事件 ----------

    public void handleAction(Player player, Envelope envelope) {
        UiSession session = find(player, envelope.session());
        if (session == null) {
            plugin.getLogger().fine(() -> player.getName() + " 发来 ACTION 但没有匹配会话，已丢弃");
            return;
        }
        Action action;
        try {
            action = JsonCodec.decode(envelope.payload(), Action.class);
        } catch (ProtocolException e) {
            plugin.getLogger().fine(() -> player.getName() + " ACTION 载荷非法: " + e.getMessage());
            return;
        }
        session.handleAction(envelope.revision(), action);
    }

    public void handleClose(Player player, int sessionId) {
        UiSession session = find(player, sessionId);
        if (session != null) {
            session.handleClientClose(sessionId);
        }
    }

    // ---------- 关闭/清理 ----------

    /** 关闭并移除玩家全部会话。 */
    public void close(Player player) {
        for (UiSession session : playerSessions(player)) {
            session.close();
        }
    }

    /** 仅当映射的仍是该会话时移除。 */
    void remove(UiSession session) {
        Map<Integer, UiSession> sessions = active.get(session.playerId());
        if (sessions == null) {
            return;
        }
        sessions.remove(session.id());
        if (sessions.isEmpty()) {
            active.remove(session.playerId(), sessions);
        }
    }

    /** 关闭某 owner 的所有会话（owner 插件停用/重载时调用）。 */
    public void closeOwned(Plugin owner) {
        for (UiSession session : allSessions()) {
            if (session.owner() == owner) {
                session.close();
            }
        }
    }

    /** 玩家退出：静默作废，不发包。 */
    public void discard(UUID playerId) {
        Map<Integer, UiSession> sessions = active.remove(playerId);
        if (sessions == null) {
            return;
        }
        for (UiSession session : sessions.values()) {
            session.discard();
        }
    }

    /** 作废全部会话（插件停用等场景），释放业务订阅。 */
    public void clear() {
        for (Map<Integer, UiSession> sessions : active.values()) {
            for (UiSession session : sessions.values()) {
                session.discard();
            }
        }
        active.clear();
    }

    // ---------- 内部 ----------

    private UiSession find(Player player, int sessionId) {
        Map<Integer, UiSession> sessions = active.get(player.getUniqueId());
        return sessions == null ? null : sessions.get(sessionId);
    }

    private List<UiSession> playerSessions(Player player) {
        Map<Integer, UiSession> sessions = active.get(player.getUniqueId());
        return sessions == null ? List.of() : new ArrayList<>(sessions.values());
    }

    private List<UiSession> allSessions() {
        List<UiSession> result = new ArrayList<>();
        for (Map<Integer, UiSession> sessions : active.values()) {
            result.addAll(sessions.values());
        }
        return result;
    }
}
