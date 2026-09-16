package com.mineui.protocol;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * MineUI 数据包信封（V1）。
 *
 * <pre>
 * MAGIC(2) | PROTOCOL(1) | TYPE(1) | SESSION(4) | REVISION(4) | FLAGS(1) | LENGTH(4) | PAYLOAD(LENGTH)
 * </pre>
 *
 * 全部多字节字段为大端（网络序）。单包 ≤ 32 KiB；压缩/分片在后续版本通过 FLAGS 启用。
 */
public final class Envelope {

    /** 'M' 'U' */
    public static final int MAGIC = 0x4D55;
    public static final int PROTOCOL_VERSION = 1;
    public static final int HEADER_SIZE = 17;
    public static final int MAX_PAYLOAD_SIZE = 32 * 1024;

    /** 载荷经 Deflate 压缩（V1 暂未启用，收到即拒绝）。 */
    public static final int FLAG_COMPRESSED = 0x01;
    /** 分片包（V1 暂未启用，收到即拒绝）。 */
    public static final int FLAG_FRAGMENTED = 0x02;
    /** 分片首片（V1 暂未启用）。 */
    public static final int FLAG_FIRST_FRAME = 0x04;
    /** 分片末片（V1 暂未启用）。 */
    public static final int FLAG_LAST_FRAME = 0x08;

    private static final int SUPPORTED_FLAGS = 0;

    private final MessageType type;
    private final int session;
    private final int revision;
    private final int flags;
    private final byte[] payload;

    public Envelope(MessageType type, int session, int revision, int flags, byte[] payload) {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload 不能为 null");
        }
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("payload 超过上限: " + payload.length);
        }
        this.type = type;
        this.session = session;
        this.revision = revision;
        this.flags = flags;
        this.payload = payload.clone();
    }

    public Envelope(MessageType type, int session, int revision, byte[] payload) {
        this(type, session, revision, 0, payload);
    }

    public MessageType type() {
        return type;
    }

    public int session() {
        return session;
    }

    public int revision() {
        return revision;
    }

    public int flags() {
        return flags;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadSize() {
        return payload.length;
    }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buf.putShort((short) MAGIC);
        buf.put((byte) PROTOCOL_VERSION);
        buf.put((byte) type.id());
        buf.putInt(session);
        buf.putInt(revision);
        buf.put((byte) flags);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    /**
     * 严格解码：任何不符（魔数/版本/类型/长度/未知标志位/超限）都抛 {@link ProtocolException}。
     */
    public static Envelope decode(byte[] data) throws ProtocolException {
        if (data == null || data.length < HEADER_SIZE) {
            throw new ProtocolException("包长度不足: " + (data == null ? "null" : data.length));
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        int magic = buf.getShort() & 0xFFFF;
        if (magic != MAGIC) {
            throw new ProtocolException("魔数不匹配: 0x" + Integer.toHexString(magic));
        }

        int protocol = buf.get() & 0xFF;
        if (protocol != PROTOCOL_VERSION) {
            throw new ProtocolException("协议版本不匹配: " + protocol + " (期望 " + PROTOCOL_VERSION + ")");
        }

        int typeId = buf.get() & 0xFF;
        MessageType type = MessageType.fromId(typeId)
                .orElseThrow(() -> new ProtocolException("未知消息类型: 0x" + Integer.toHexString(typeId)));

        int session = buf.getInt();
        int revision = buf.getInt();

        int flags = buf.get() & 0xFF;
        if ((flags & ~SUPPORTED_FLAGS) != 0) {
            throw new ProtocolException("不支持的标志位: 0x" + Integer.toHexString(flags));
        }

        int length = buf.getInt();
        if (length < 0 || length > MAX_PAYLOAD_SIZE) {
            throw new ProtocolException("载荷长度非法: " + length);
        }
        if (data.length != HEADER_SIZE + length) {
            throw new ProtocolException("载荷长度与包总长不符: 声明 " + length + ", 实际 " + (data.length - HEADER_SIZE));
        }

        byte[] payload = new byte[length];
        buf.get(payload);
        return new Envelope(type, session, revision, flags, payload);
    }

    @Override
    public String toString() {
        return "Envelope[" + type + " session=" + session + " revision=" + revision
                + " flags=" + flags + " payload=" + payload.length + "B]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Envelope other)) {
            return false;
        }
        return session == other.session
                && revision == other.revision
                && flags == other.flags
                && type == other.type
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * type.hashCode() + session) + revision) + flags) + Arrays.hashCode(payload);
    }
}
