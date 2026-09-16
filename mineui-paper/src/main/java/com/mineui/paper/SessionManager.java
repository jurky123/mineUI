package com.mineui.paper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 在线 MineUI 客户端会话表。线程安全：HELLO 在 Netty 线程写入，命令在主线程读取。 */
public final class SessionManager {

    /**
     * 一次握手的客户端信息。
     *
     * @param protocol        协议版本
     * @param modVersion      客户端 mod 版本
     * @param minecraft       Minecraft 版本
     * @param capabilities    能力位
     * @param connectedAtMillis 握手时间
     */
    public record ClientSession(int protocol, String modVersion, String minecraft,
                                List<String> capabilities, long connectedAtMillis) {

        public ClientSession {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    private final Map<UUID, ClientSession> sessions = new ConcurrentHashMap<>();

    public void register(UUID playerId, ClientSession session) {
        sessions.put(playerId, session);
    }

    public ClientSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean isModClient(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public int count() {
        return sessions.size();
    }

    public void clear() {
        sessions.clear();
    }
}
