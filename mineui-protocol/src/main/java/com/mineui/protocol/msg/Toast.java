package com.mineui.protocol.msg;

import java.util.Locale;

/**
 * 服务端短提示（切歌、错误等）。
 *
 * @param text           文本
 * @param icon           图标物品 id（如 {@code minecraft:music_disc_cat}；空表示无图标）
 * @param action         点击动作 id（空表示不可点击；客户端通过全局动作回传）
 * @param durationMillis 显示时长（毫秒；≤0 用默认值）
 * @param color          文字颜色（ARGB）
 * @param kind           样式：info / success / warn / error（决定描边色；空视为 info）
 */
public record Toast(String text, String icon, String action, int durationMillis, int color, String kind) {

    public static final int DEFAULT_DURATION_MILLIS = 4000;

    public Toast {
        text = text == null ? "" : text;
        icon = icon == null ? "" : icon;
        action = action == null ? "" : action;
        durationMillis = durationMillis <= 0 ? DEFAULT_DURATION_MILLIS : durationMillis;
        kind = normalizeKind(kind);
    }

    private static String normalizeKind(String kind) {
        if (kind == null) {
            return "info";
        }
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "success", "warn", "warning", "error" -> kind.toLowerCase(Locale.ROOT);
            default -> "info";
        };
    }

    /** 无图标、不可点击、默认色、info 样式。 */
    public static Toast of(String text) {
        return new Toast(text, "", "", DEFAULT_DURATION_MILLIS, 0xFFFFFFFF, "info");
    }
}
