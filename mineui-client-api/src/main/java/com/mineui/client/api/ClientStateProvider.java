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

    /**
     * 本地图片字节（FR-19）：当页面写 {@code "texture": "{local.<namespace>.<key>}"} 时，
     * MineUI 调用该命名空间 provider 的此方法取图。
     * <p>
     * 返回该短键对应的图片原始字节（ImageIO 可解码：PNG/JPEG/GIF/BMP），
     * {@code null} 表示无本地图片，按 URL 逻辑回退。实现必须**无阻塞、无网络、无重解码**
     * （返回业务侧已缓存的字节），调用线程为渲染/逻辑线程。
     * <p>
     * 默认返回 {@code null}：不实现即与旧行为一致（向后兼容）。
     */
    default byte[] image(String key) {
        return null;
    }
}
