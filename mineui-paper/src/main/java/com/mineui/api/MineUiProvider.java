package com.mineui.api;

/** MineUI 服务定位器：由 mineui-paper 在启用时注册，业务插件只读。 */
public final class MineUiProvider {

    private static volatile MineUi instance;

    private MineUiProvider() {
    }

    /** MineUI 是否可用（未安装/未启用时为 null）。 */
    public static MineUi get() {
        return instance;
    }

    public static void register(MineUi api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
