package com.mineui.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.spec.UiSpecParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端主题 token（config/mineui/theme.json）：业务页面用 {@code "skin": "mineui:accent"}
 * 即可获得玩家自定义的强调色，页面本身不写死颜色。
 * <pre>
 * { "accent": "#FF3FA9F5", "accentHover": "#FF6FC0FA" }
 * </pre>
 */
public final class MineUiTheme {

    private static final int DEFAULT_ACCENT = 0xFF3FA9F5;

    /** 主题值（不可变整体，保证 accent/accentHover 成对原子更新）。 */
    private record Theme(int accent, int accentHover) {
    }

    private static volatile Theme current =
            new Theme(DEFAULT_ACCENT, lighten(DEFAULT_ACCENT));
    private static volatile boolean loaded;

    private MineUiTheme() {
    }

    /** 重新读取主题文件（客户端初始化 / F9 热重载时调用）。 */
    public static synchronized void load() {
        Path file = configPath();
        Theme next = current;
        if (Files.isRegularFile(file)) {
            try {
                JsonObject json = com.google.gson.JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                int accent = json.has("accent")
                        ? parseColor(json.get("accent").getAsString(), DEFAULT_ACCENT) : DEFAULT_ACCENT;
                int accentHover = json.has("accentHover")
                        ? parseColor(json.get("accentHover").getAsString(),
                        com.mineui.ui.paint.UiColors.lerp(accent, 0xFFFFFFFF, 0.2f)) : lighten(accent);
                next = new Theme(accent, accentHover);
            } catch (Exception e) {
                MineUiClient.LOGGER.warn("读取 MineUI 主题失败，使用默认: {}", e.getMessage());
                next = new Theme(DEFAULT_ACCENT, lighten(DEFAULT_ACCENT));
            }
        } else {
            next = new Theme(DEFAULT_ACCENT, lighten(DEFAULT_ACCENT));
        }
        current = next;
        loaded = true;
    }

    /** 页面背景强调色（皮肤 mineui:accent 使用）。 */
    public static int accent() {
        ensureLoaded();
        return current.accent();
    }

    /** 强调色悬停/高亮变体。 */
    public static int accentHover() {
        ensureLoaded();
        return current.accentHover();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static int lighten(int color) {
        return com.mineui.ui.paint.UiColors.lerp(color, 0xFFFFFFFF, 0.3f);
    }

    private static int parseColor(String text, int fallback) {
        try {
            return UiSpecParser.parseColor(new com.google.gson.JsonPrimitive(text), fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("mineui").resolve("theme.json");
    }
}
