package com.mineui.client.ui;

import net.minecraft.client.Minecraft;

/** 当前 MineUI 界面的打开/关闭（渲染线程）。 */
public final class MineUiScreens {

    private static UiScreen current;

    private MineUiScreens() {
    }

    /** 服务端 OPEN：打开（或替换）界面。 */
    public static void open(String app, String view) {
        UiScreen screen = new UiScreen(app, view);
        current = screen;
        Minecraft.getInstance().gui.setScreen(screen);
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
}
