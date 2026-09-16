package com.mineui.paper;

import com.mineui.protocol.Envelope;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 插件消息发送封装。
 * 网络回调（Netty 线程）里必须用 {@link #sendLater}，避免跨线程触碰 Bukkit API。
 */
public final class Transport {

    private final MineUiPlugin plugin;

    Transport(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    /** 主线程调用。 */
    public void sendNow(Player player, Envelope envelope) {
        if (!player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, MineUiPlugin.CHANNEL, envelope.encode());
    }

    /** 任意线程调用：调度回主线程发送。 */
    public void sendLater(Player player, Envelope envelope) {
        byte[] bytes = envelope.encode();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, MineUiPlugin.CHANNEL, bytes);
            }
        });
    }
}
