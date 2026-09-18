package com.mineui.paper;

import com.mineui.api.MineUi;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.msg.Keybind;
import com.mineui.protocol.msg.Keybinds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每玩家声明的客户端键位（槽位 → 全局动作），按 owner 管理生命周期。
 * 玩家加入（HELLO）时全量重发；owner 停用时清理并重发。
 */
public final class KeybindManager {

    private record Entry(Plugin owner, String action, String label) {
    }

    private final MineUiPlugin plugin;
    private final Map<UUID, Map<String, Entry>> byPlayer = new ConcurrentHashMap<>();

    KeybindManager(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    public void set(Plugin owner, Player player, String slot, String action, String label) {
        if (owner == null || slot == null || slot.isBlank() || action == null || action.isBlank()) {
            return;
        }
        byPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(slot, new Entry(owner, action, label == null ? "" : label));
        sendTo(player);
    }

    public void clear(Plugin owner, Player player) {
        Map<String, Entry> map = byPlayer.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        map.values().removeIf(entry -> entry.owner() == owner);
        sendTo(player);
    }

    /** owner 停用：清理其在所有玩家上的声明并重发。 */
    public void clearOwned(Plugin owner) {
        for (Map.Entry<UUID, Map<String, Entry>> playerEntry : byPlayer.entrySet()) {
            Map<String, Entry> map = playerEntry.getValue();
            boolean changed = map.values().removeIf(entry -> entry.owner() == owner);
            if (!changed) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player != null && player.isOnline()) {
                sendTo(player);
            }
        }
        byPlayer.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void clearPlayer(UUID playerId) {
        byPlayer.remove(playerId);
    }

    /** 主线程：全量下发该玩家的键位声明（客户端不支持该能力时不发送）。 */
    public void sendTo(Player player) {
        SessionManager.ClientSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !session.capabilities().contains(MineUi.CAPABILITY_KEYBIND)) {
            return;
        }
        List<Keybind> binds = new ArrayList<>();
        Map<String, Entry> map = byPlayer.get(player.getUniqueId());
        if (map != null) {
            for (Map.Entry<String, Entry> slot : map.entrySet()) {
                binds.add(new Keybind(slot.getKey(), slot.getValue().action(), slot.getValue().label()));
            }
            binds.sort(Comparator.comparing(Keybind::slot));
        }
        plugin.transport().sendNow(player,
                new Envelope(MessageType.KEYBIND, 0, 0, JsonCodec.encode(new Keybinds(binds))));
    }

    public void clear() {
        byPlayer.clear();
    }
}
