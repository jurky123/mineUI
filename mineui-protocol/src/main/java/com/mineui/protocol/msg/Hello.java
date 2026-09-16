package com.mineui.protocol.msg;

import java.util.List;

/**
 * 客户端 → 服务端握手。
 *
 * @param protocol     协议版本（当前 1）
 * @param modVersion   客户端 mod 版本
 * @param minecraft    Minecraft 版本（如 "26.2"）
 * @param capabilities 能力位（如 "screen"、"hud"、"item_render"）
 */
public record Hello(int protocol, String modVersion, String minecraft, List<String> capabilities) {

    public Hello {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
