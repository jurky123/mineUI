package com.mineui.client.ui;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import com.mineui.ui.spec.UiDefinition;
import com.mineui.ui.spec.UiDefinitionLoader;
import com.mineui.ui.spec.UiSpecException;
import com.mineui.ui.tree.ColumnNode;
import com.mineui.ui.tree.CrossAlign;
import com.mineui.ui.tree.MainAlign;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.TextNode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** 当前 MineUI 界面的打开/关闭/热重载（渲染线程）。 */
public final class MineUiScreens {

    private static final Logger LOGGER = LoggerFactory.getLogger("MineUI");

    private static UiScreen current;

    private MineUiScreens() {
    }

    /** 服务端 OPEN：加载定义并打开界面。 */
    public static void open(String app, String view) {
        UiDefinition definition;
        try {
            definition = UiDefinitionLoader.load(app, view, devRoot());
            LOGGER.info("加载界面 {} / {}（来源: {}）", app, view, definition.source());
            if ("mod".equals(definition.source())) {
                Path file = UiDefinitionLoader.devFile(app, view, devRoot());
                if (!Files.isRegularFile(file)) {
                    chat("[MineUI] 按 F10 可导出可编辑副本（config/mineui/ui/" + app + "/" + view + ".json）");
                }
            }
        } catch (UiSpecException e) {
            LOGGER.warn("界面定义加载失败: {}", e.getMessage());
            definition = errorDefinition(app, view, e.getMessage());
            chat("[MineUI] 界面定义加载失败: " + e.getMessage());
        }
        UiScreen screen = new UiScreen(definition);
        current = screen;
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** F9：清缓存并重新加载当前界面（含开发目录覆盖）。 */
    public static void reload() {
        UiDefinitionLoader.clearCache();
        UiScreen screen = current;
        if (screen == null) {
            chat("[MineUI] 没有打开的界面，无法重载");
            return;
        }
        open(screen.app(), screen.view());
        chat("[MineUI] 已重载 " + screen.app() + "/" + screen.view());
    }

    /** F10：把当前界面的内置定义导出到开发目录（不覆盖已有文件）。 */
    public static void exportDevTemplate() {
        UiScreen screen = current;
        if (screen == null) {
            chat("[MineUI] 没有打开的界面，无法导出");
            return;
        }
        Path file = UiDefinitionLoader.devFile(screen.app(), screen.view(), devRoot());
        try {
            if (UiDefinitionLoader.writeDevTemplate(screen.app(), screen.view(), devRoot())) {
                LOGGER.info("已导出开发模板: {}", file);
                chat("[MineUI] 已导出到 config/mineui/ui/" + screen.app() + "/" + screen.view() + ".json（F9 重载生效）");
            } else {
                chat("[MineUI] 开发模板已存在，未覆盖（直接编辑后按 F9）");
            }
        } catch (UiSpecException e) {
            LOGGER.warn("导出开发模板失败: {}", e.getMessage());
            chat("[MineUI] 导出失败: " + e.getMessage());
        }
    }

    /** 服务端 CLOSE：关闭界面（不回调 onClose，避免回发 CLOSE）。 */
    public static void closeFromServer() {
        UiScreen screen = current;
        current = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (screen != null && minecraft.gui.screen() == screen) {
            minecraft.gui.setScreen(null);
        }
    }

    /** 玩家自行关闭（Esc）：只清理引用。 */
    static void clientClosed(UiScreen screen) {
        if (current == screen) {
            current = null;
        }
    }

    public static boolean isOpen() {
        return current != null;
    }

    /** 开发覆盖目录：<config>/mineui/ui/<app>/<view>.json */
    private static Path devRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve("mineui").resolve("ui");
    }

    private static void chat(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static UiDefinition errorDefinition(String app, String view, String message) {
        NodeStyle rootStyle = new NodeStyle("error", SizeSpec.px(420), SizeSpec.auto(), Insets.all(12),
                0xE0300000, CrossAlign.START, MainAlign.START, 4);
        ColumnNode root = new ColumnNode(rootStyle);
        root.addChild(new TextNode(NodeStyle.defaults(), "MineUI 界面加载失败: " + app + "/" + view, 0xFFFF6666, 1f));
        root.addChild(new TextNode(NodeStyle.defaults(), message == null ? "" : message, 0xFFFFAAAA, 1f));
        root.addChild(new TextNode(NodeStyle.defaults(), "按 F9 重试 · Esc 关闭", 0xFF909090, 1f));
        return new UiDefinition(app, view, "error", root);
    }
}
