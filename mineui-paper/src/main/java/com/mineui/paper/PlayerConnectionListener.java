package com.mineui.paper;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 加入/退出：握手超时判定 + 会话清理。 */
final class PlayerConnectionListener implements Listener {

    /** 客户端加入后 3 秒内未收到 HELLO 即判定为原版客户端。 */
    private static final long HANDSHAKE_TIMEOUT_TICKS = 60L;

    private final MineUiPlugin plugin;

    PlayerConnectionListener(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.sessions().isModClient(player.getUniqueId())) {
                return;
            }
            plugin.getLogger().info(player.getName() + " 未收到 MineUI 握手，判定为原版客户端 (VANILLA)");
        }, HANDSHAKE_TIMEOUT_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().remove(event.getPlayer().getUniqueId());
    }
}
