package com.mineui.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineui.protocol.msg.HudLayout;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端本地偏好（config/mineui/client.json）：
 * HUD 总开关与逐 app/view 覆盖（位置/缩放/开关），本地优先于服务端默认值。
 */
public final class MineUiConfig {

    /** 单个 HUD 的本地覆盖（null 字段表示不覆盖）。 */
    public static final class HudPref {
        public Boolean enabled;
        public String anchor;
        public Float offsetX;
        public Float offsetY;
        public Float scale;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static MineUiConfig instance;

    /** HUD 总开关（F6 切换）。 */
    public boolean hudEnabled = true;
    /** 打开任意界面（背包/聊天/MineUI 屏幕）时自动隐藏 HUD，减少遮挡。 */
    public boolean hudHideOnScreen = true;
    /** key = "app/view" */
    public Map<String, HudPref> hud = new HashMap<>();

    public static synchronized MineUiConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static MineUiConfig load() {
        Path file = file();
        if (Files.isRegularFile(file)) {
            try {
                MineUiConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MineUiConfig.class);
                if (loaded != null) {
                    if (loaded.hud == null) {
                        loaded.hud = new HashMap<>();
                    }
                    return loaded;
                }
            } catch (Exception e) {
                MineUiClient.LOGGER.warn("读取客户端配置失败，使用默认值: {}", e.getMessage());
            }
        }
        return new MineUiConfig();
    }

    public synchronized void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineUiClient.LOGGER.warn("保存客户端配置失败: {}", e.getMessage());
        }
    }

    /** 该 HUD 是否启用：逐页覆盖 > 总开关。 */
    public boolean isHudEnabled(String app, String view) {
        HudPref pref = hud.get(app + "/" + view);
        if (pref != null && pref.enabled != null) {
            return pref.enabled;
        }
        return hudEnabled;
    }

    /** 本地覆盖优先的服务端布局合并。 */
    public HudLayout effectiveLayout(String app, String view, HudLayout server) {
        HudLayout base = server == null ? HudLayout.defaults() : server;
        HudPref pref = hud.get(app + "/" + view);
        if (pref == null) {
            return base;
        }
        return new HudLayout(
                pref.anchor != null ? pref.anchor : base.anchor(),
                pref.offsetX != null ? pref.offsetX : base.offsetX(),
                pref.offsetY != null ? pref.offsetY : base.offsetY(),
                pref.scale != null ? pref.scale : base.scale());
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mineui").resolve("client.json");
    }
}
