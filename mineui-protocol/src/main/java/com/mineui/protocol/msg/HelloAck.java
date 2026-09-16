package com.mineui.protocol.msg;

/**
 * 服务端 → 客户端握手应答。
 *
 * @param protocol      服务端协议版本
 * @param serverVersion 服务端插件版本
 * @param minimumClient 要求的最低客户端版本
 */
public record HelloAck(int protocol, String serverVersion, String minimumClient) {
}
