package com.mineui.protocol;

import java.util.Arrays;
import java.util.Optional;

/**
 * MineUI 消息类型（信封 TYPE 字段）。
 * <p>
 * 已分配的值永不改变；新增类型只追加，保证新旧版本兼容。
 */
public enum MessageType {

    /** 客户端 → 服务端：握手、版本、能力 */
    HELLO(0x01),
    /** 服务端 → 客户端：握手应答 */
    HELLO_ACK(0x02),
    /** 服务端 → 客户端：打开界面 */
    OPEN(0x03),
    /** 双向：关闭界面 */
    CLOSE(0x04),
    /** 服务端 → 客户端：完整状态 */
    SNAPSHOT(0x05),
    /** 服务端 → 客户端：增量状态 */
    PATCH(0x06),
    /** 客户端 → 服务端：用户操作 */
    ACTION(0x07),
    /** 服务端 → 客户端：操作接受（乐观动画） */
    ACTION_ACCEPTED(0x08),
    /** 服务端 → 客户端：操作拒绝（乐观动画回滚） */
    ACTION_REJECTED(0x09),
    /** 服务端 → 客户端：远程资源清单 */
    ASSET_MANIFEST(0x0A),
    /** 双向：延迟测量 */
    PING(0x0B),
    /** 双向：延迟测量应答 */
    PONG(0x0C),
    /** 服务端 → 客户端：远程图片策略（白名单/大小/缓存上限） */
    REMOTE_POLICY(0x0D),
    /** 服务端 → 客户端：短提示（切歌/错误等） */
    TOAST(0x0E);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<MessageType> fromId(int id) {
        return Arrays.stream(values()).filter(t -> t.id == id).findFirst();
    }
}
