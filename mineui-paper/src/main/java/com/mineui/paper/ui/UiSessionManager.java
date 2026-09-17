package com.mineui.paper.ui;

import com.mineui.paper.MineUiPlugin;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Action;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线界面会话管理：每个玩家同时只允许一个 MineUI 会话（V1 策略）。
 * <p>
 * 会话的创建/关闭/动作分发均在主线程执行（由插件消息回调调度过来）。
 */
public final class UiSessionManager {

    private final MineUiPlugin plugin;
    private final Map<UUID, UiSession> active = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public UiSessionManager(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    /** 打开新会话（会先关闭该玩家已有会话），发送 OPEN。业务代码随后设置 state 并 snapshot()。 */
    public UiSession open(Player player, String app, String view) {
        return open(plugin, player, app, view);
    }

    /** 以指定 owner 打开新会话（owner 停用时由 {@link #closeOwned(Plugin)} 统一关闭）。 */
    public UiSession open(Plugin owner, Player player, String app, String view) {
        close(player);
        UiSession session = new UiSession(plugin, owner, player, nextId.getAndIncrement(), app, view);
        active.put(player.getUniqueId(), session);
        session.open();
        return session;
    }

    public UiSession get(Player player) {
        return active.get(player.getUniqueId());
    }

    public int count() {
        return active.size();
    }

    public void handleAction(Player player, Envelope envelope) {
        UiSession session = active.get(player.getUniqueId());
        if (session == null) {
            plugin.getLogger().fine(() -> player.getName() + " 发来 ACTION 但没有活动会话，已丢弃");
            return;
        }
        if (!session.matches(envelope.session())) {
            // 旧页面（已关闭/被替换）的延迟包：不得打到当前会话
            plugin.getLogger().fine(() -> player.getName() + " ACTION 会话不匹配，已丢弃（收到 "
                    + envelope.session() + "，当前 " + session.id() + "）");
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
        UiSession session = active.get(player.getUniqueId());
        if (session != null) {
            session.handleClientClose(sessionId);
        }
    }

    /** 关闭并移除玩家会话（无会话时无操作）。 */
    public void close(Player player) {
        UiSession session = active.get(player.getUniqueId());
        if (session != null) {
            session.close();
        }
    }

    /** 仅当映射的仍是该会话时移除（防止旧会话关闭时误删新会话）。 */
    void remove(UiSession session) {
        active.remove(session.playerId(), session);
    }

    /** 关闭某 owner 的所有会话（owner 插件停用/重载时调用）。 */
    public void closeOwned(Plugin owner) {
        for (UiSession session : active.values()) {
            if (session.owner() == owner) {
                session.close();
            }
        }
    }

    /** 玩家退出：静默作废，不发包。 */
    public void discard(UUID playerId) {
        UiSession session = active.remove(playerId);
        if (session != null) {
            session.discard();
        }
    }

    /** 作废全部会话（插件停用等场景），释放业务订阅。 */
    public void clear() {
        for (UiSession session : active.values()) {
            session.discard();
        }
        active.clear();
    }
}
