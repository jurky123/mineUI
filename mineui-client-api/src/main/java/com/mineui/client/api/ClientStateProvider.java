package com.mineui.client.api;

/**
 * 客户端本地状态提供者（业务客户端 mod 实现）。
 * <p>
 * 由 MineUI 在客户端渲染/逻辑线程每帧调用，实现必须无阻塞、无网络、无重计算。
 * 绑定语法 {@code {local.<namespace>.<key>}} 会读取这里的值。
 */
@FunctionalInterface
public interface ClientStateProvider {

    /** 无该键返回 null（MineUI 会渲染为空串）。 */
    Object get(String key);

    /**
     * 结构代数：当本地数据发生**结构性变化**（如列表项增删、文本长度明显变化需要重排）时自增。
     * <p>
     * 纯数值变化（播放位置等）无需自增——MineUI 每帧直接读取，不需要重排。
     * 默认 0 表示从不主动触发重排。
     */
    default long generation() {
        return 0L;
    }
}
