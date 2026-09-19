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

    /** 能力位：客户端支持 Toast 短提示与全局动作（0.10+）。 */
    String CAPABILITY_TOAST = "toast";

    /** 能力位：客户端支持服务端声明的通用键位（0.11+）。 */
    String CAPABILITY_KEYBIND = "keybind";

    /** 玩家是否已安装并握手 MineUI 客户端 mod。 */
    boolean hasClient(Player player);

    /** 客户端上报的能力位；未安装 mod 时为空集合。 */
    Set<String> capabilities(Player player);

    /**
     * 客户端 mod 版本（如 {@code "0.14.0"}）；未握手时返回 null。
     * 业务下发新控件页面前可用它做版本门禁（配合 {@code com.mineui.protocol.SemVer} 比较）。
     */
    default String modVersion(Player player) {
        return null;
    }

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

    /** 客户端是否支持 Toast；不支持时业务应降级（如改用聊天提示）。 */
    default boolean supportsToast(Player player) {
        return capabilities(player).contains(CAPABILITY_TOAST);
    }

    /**
     * 下发短提示（切歌、错误等）。不需要会话；客户端不支持时静默忽略。
     *
     * @param actionId 点击动作 id（空表示不可点击）；点击后由 {@link #onAction} 注册的处理器接收
     */
    void toast(Player player, com.mineui.protocol.msg.Toast toast);

    /** 便捷下发（无图标、指定时长、不可点击、默认白色）。 */
    default void toast(Player player, String text, int durationMillis) {
        toast(player, new com.mineui.protocol.msg.Toast(text, "", "", durationMillis, 0xFFFFFFFF, "info"));
    }

    /** 便捷下发：样式 kind 为 info / success / warn / error（决定描边色）。 */
    default void toast(Player player, String text, String icon, int durationMillis, String actionId, String kind) {
        toast(player, new com.mineui.protocol.msg.Toast(text, icon, actionId, durationMillis, 0xFFFFFFFF, kind));
    }

    /**
     * 注册全局动作处理器（session=0 的 ACTION）：Toast 点击、键位等无会话操作。
     * 同一 owner 对同一 id 重复注册会覆盖；owner 插件停用时自动清理。
     * <p>
     * 命名约定：不同插件注册同名 id 会<b>全部触发</b>（并记冲突警告），
     * 因此 actionId 必须带插件命名空间，如 {@code "mineaudio:open_ui"}。
     */
    void onAction(Plugin owner, String actionId, java.util.function.Consumer<MineUiAction> handler);

    /** 客户端是否支持服务端声明的键位；不支持时业务应降级（如只提供界面按钮）。 */
    default boolean supportsKeybind(Player player) {
        return capabilities(player).contains(CAPABILITY_KEYBIND);
    }

    /**
     * 声明一条客户端键位：按下槽位对应的按键时回传全局动作。
     *
     * @param owner    所有者：插件停用时其键位声明自动清理
     * @param slot     客户端键位池槽位（"1".."8"）；默认 1=F7、2=F8，其余留空由玩家在原版按键设置中绑定
     * @param actionId 按键触发的全局动作 id（用 {@link #onAction} 注册处理器）
     * @param label    展示名（页面 {@code {key.<actionId>}} 提示可用）
     */
    void keybind(Plugin owner, Player player, String slot, String actionId, String label);

    /** 清除该 owner 在玩家上的全部键位声明。 */
    void clearKeybinds(Plugin owner, Player player);

    /** 关闭某插件拥有的全部会话（含 HUD）。 */
    void closeAll(Plugin owner);
}
