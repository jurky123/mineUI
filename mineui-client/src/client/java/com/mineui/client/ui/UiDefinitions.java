package com.mineui.client.ui;

import com.google.gson.JsonObject;
import com.mineui.ui.spec.UiDefinition;
import com.mineui.ui.spec.UiDefinitionLoader;
import com.mineui.ui.spec.UiSpecException;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端界面定义加载：开发目录覆盖 &gt; 服务端下发 &gt; mod 内置（屏幕与 HUD 共用）。
 */
public final class UiDefinitions {

    private UiDefinitions() {
    }

    public static UiDefinition load(String app, String view, JsonObject provided) throws UiSpecException {
        Path devRoot = devRoot();
        if (provided != null) {
            if (Files.isRegularFile(UiDefinitionLoader.devFile(app, view, devRoot))) {
                return UiDefinitionLoader.load(app, view, devRoot);
            }
            return UiDefinitionLoader.loadProvided(app, view, provided);
        }
        return UiDefinitionLoader.load(app, view, devRoot);
    }

    public static Path devRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("mineui").resolve("ui");
    }
}
