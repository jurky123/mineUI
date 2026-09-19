package com.mineui.client.api;

/**
 * 业务客户端注册本地状态与动作的入口。
 * <p>
 * 纯 Java 注册表，由 mineui-client-api 自带；MineUI 客户端在运行时消费，
 * MineUI 不在时注册表只是没人消费，业务客户端正常加载（无副作用）。
 */
public interface MineUiClientBridge {

    /** 获取注册桥（单例，纯静态，无 MC 依赖）。 */
    static MineUiClientBridge get() {
        return MineUiClientRegistry.bridge();
    }

    /**
     * 注册一个命名空间。
     *
     * @param namespace 命名空间（如 {@code "mineaudio"}），非空
     * @param state     本地状态提供者（可为 null）
     * @param actions   本地动作处理器（可为 null）
     * @return 注销句柄；关闭后对应 {@code local.*} 绑定渲染为空串、{@code local:} 动作忽略
     */
    AutoCloseable register(String namespace, ClientStateProvider state, ClientActionHandler actions);

    /** 追加一个随 MineUI HELLO 上报给服务端的能力位。 */
    void declareCapability(String capability);
}
