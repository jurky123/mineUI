package com.mineui.client.api;

import java.util.Map;

/**
 * 客户端本地动作处理器（业务客户端 mod 实现）。
 * <p>
 * 只接收同命名空间的 {@code local:<namespace>.<action>}；实现应在客户端线程内快速完成。
 */
@FunctionalInterface
public interface ClientActionHandler {

    /**
     * @param action  {@code local:<namespace>.<action>} 中命名空间之后的部分（如 {@code "seek"}）
     * @param payload 动作负载（无负载为空 Map；值类型为 String / Number / Boolean）
     * @return true 表示已处理；false 表示未处理（MineUI 忽略，不回传服务端）
     */
    boolean handle(String action, Map<String, Object> payload);
}
