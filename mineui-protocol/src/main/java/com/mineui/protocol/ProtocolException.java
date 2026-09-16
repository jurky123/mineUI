package com.mineui.protocol;

/** 协议编解码/校验失败。数据来自网络，调用方必须捕获并拒绝该包，而不是信任。 */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
