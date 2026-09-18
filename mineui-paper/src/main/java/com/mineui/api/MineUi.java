package com.mineui.api;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * MineUI 对外入口（稳定的业务插件 API）。
 * <p>
 * 用法（界面定义随业务插件 jar 发布，通过 {@link #open} 下发）：
 * <pre>{@code
 * MineUi mineUi = MineUiProvider.get();
 * if (mineUi != null && mineUi.hasClient(player)) {
 *     JsonObject ui = loadResource("assets/example/ui/example/main.json");
 *     MineUiSession session = mineUi.open(this, player, "example", "main", ui);
 *     session.state("title", "示例界面");
 *     session.on("close", action -> session.close());
 *     session.snapshot();
 * }
 * }</pre>
 * 所有方法必须在主线程调用。
 */
public interface MineUi {

    /** 能力位：客户端支持在 OPEN 中接收服务端下发的界面定义（0.6.4+）。 */
    String CAPABILITY_SERVER_UI = "server_ui";

    /** 能力位：客户端支持 HUD 会话（0.8+）。 */
    String CAPABILITY_HUD = "hud_v2";

    /** 玩家是否已安装并握手 MineUI 客户端 mod。 */
    boolean hasClient(Player player);

    /** 客户端上报的能力位；未安装 mod 时为空集合。 */
    Set<String> capabilities(Player player);

    /**
     * 客户端是否支持随 OPEN 下发界面定义。
     * 业务插件应先确认该能力，不支持时回退到原版界面或提示，避免开出一个加载失败的页面。
     */
    default boolean supportsServerUi(Player player) {
        return capabilities(player).contains(CAPABILITY_SERVER_UI);
    }

    /** 客户端是否支持 HUD 会话；不支持时业务应降级（如不显示 HUD）。 */
    default boolean supportsHud(Player player) {
        return capabilities(player).contains(CAPABILITY_HUD);
    }

    /**
     * 打开界面会话（会先关闭该玩家已有会话），界面定义由客户端内置/开发目录加载。
     *
     * @param owner 会话所有者：owner 插件停用时其所有会话会被自动关闭
     */
    default MineUiSession open(Plugin owner, Player player, String app, String view) {
        return open(owner, player, app, view, null);
    }

    /**
     * 打开界面会话（会先关闭该玩家已有会话），并携带业务插件自带的界面定义。
     *
     * @param owner      会话所有者：owner 插件停用时其所有会话会被自动关闭
     * @param definition 界面定义 JSON（业务插件资源，如 {@code assets/<plugin>/ui/<app>/<view>.json}）；
     *                   null 表示由客户端内置/开发目录加载
     */
    MineUiSession open(Plugin owner, Player player, String app, String view, JsonObject definition);

    /**
     * 打开 HUD 会话（不占用屏幕；与屏幕会话并存，owner 生命周期一致）。
     *
     * @param owner      会话所有者
     * @param definition 界面定义 JSON（null 表示由客户端内置/开发目录加载）
     * @param layout     HUD 布局（null 用默认值；客户端本地偏好可覆盖）
     */
    MineUiSession openHud(Plugin owner, Player player, String app, String view,
                          JsonObject definition, com.mineui.protocol.msg.HudLayout layout);

    /** 打开 HUD 会话（默认布局）。 */
    default MineUiSession openHud(Plugin owner, Player player, String app, String view, JsonObject definition) {
        return openHud(owner, player, app, view, definition, com.mineui.protocol.msg.HudLayout.defaults());
    }

    /** 关闭某插件拥有的全部会话（含 HUD）。 */
    void closeAll(Plugin owner);
}
