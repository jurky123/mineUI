package com.mineui.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * MineUI 对外入口（稳定的业务插件 API）。
 * <p>
 * 用法：
 * <pre>{@code
 * MineUi mineUi = MineUiProvider.get();
 * if (mineUi != null && mineUi.hasClient(player)) {
 *     MineUiSession session = mineUi.open(this, player, "skin", "browser");
 *     session.state("title", "皮肤浏览器");
 *     session.on("close", action -> session.close());
 *     session.snapshot();
 * }
 * }</pre>
 * 所有方法必须在主线程调用。
 */
public interface MineUi {

    /** 玩家是否已安装并握手 MineUI 客户端 mod。 */
    boolean hasClient(Player player);

    /**
     * 打开界面会话（会先关闭该玩家已有会话）。
     *
     * @param owner 会话所有者：owner 插件停用时其所有会话会被自动关闭
     */
    MineUiSession open(Plugin owner, Player player, String app, String view);

    /** 关闭某插件拥有的全部会话。 */
    void closeAll(Plugin owner);
}
