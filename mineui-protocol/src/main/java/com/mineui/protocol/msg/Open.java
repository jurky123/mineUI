package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * 服务端 → 客户端：打开界面（屏幕或 HUD）。信封 SESSION 字段即会话 id。
 *
 * @param app    业务命名空间（如 "mineaudio"）
 * @param view   视图名
 * @param ui     可选的界面定义（业务插件自带页面）；null 表示从客户端内置/开发目录加载
 * @param mode   {@code "screen"}（默认）或 {@code "hud"}
 * @param layout HUD 布局（仅 mode=hud 时有效；null 用默认值）
 */
public record Open(String app, String view, JsonObject ui, String mode, HudLayout layout) {

    public static final String MODE_SCREEN = "screen";
    public static final String MODE_HUD = "hud";

    public Open {
        mode = mode == null || mode.isBlank() ? MODE_SCREEN : mode;
    }

    public Open(String app, String view) {
        this(app, view, null, MODE_SCREEN, null);
    }

    public Open(String app, String view, JsonObject ui) {
        this(app, view, ui, MODE_SCREEN, null);
    }

    public boolean isHud() {
        return MODE_HUD.equals(mode);
    }
}
