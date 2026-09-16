package com.mineui.client.net;

import com.mineui.client.MineUiClient;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.HelloAck;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 客户端协议层：握手、信封收发。
 * 所有方法均在渲染线程调用（接收回调已由入口切线程）。
 */
public final class ProtocolClient {

    private static volatile int serverProtocol = -1;

    private ProtocolClient() {
    }

    /** 服务端协商出的协议版本；-1 表示尚未握手成功。 */
    public static int serverProtocol() {
        return serverProtocol;
    }

    public static void sendHello() {
        Hello hello = new Hello(
                Envelope.PROTOCOL_VERSION,
                MineUiClient.version(),
                MineUiClient.MINECRAFT_VERSION,
                List.of("screen", "hud"));
        send(new Envelope(MessageType.HELLO, 0, 0, JsonCodec.encode(hello)));
    }

    public static void send(Envelope envelope) {
        if (!ClientPlayNetworking.canSend(MineUiPayload.ID)) {
            MineUiClient.LOGGER.warn("服务端未注册 {} 通道，丢弃 {}", MineUiPayload.ID, envelope.type());
            return;
        }
        ClientPlayNetworking.send(new MineUiPayload(envelope.encode()));
    }

    public static void handleIncoming(byte[] raw) {
        Envelope envelope;
        try {
            envelope = Envelope.decode(raw);
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("收到非法 MineUI 包: {}", e.getMessage());
            return;
        }

        switch (envelope.type()) {
            case HELLO_ACK -> handleHelloAck(envelope);
            case PING -> send(new Envelope(MessageType.PONG, envelope.session(), envelope.revision(), envelope.payload()));
            default -> MineUiClient.LOGGER.debug("忽略消息 {}", envelope.type());
        }
    }

    private static void handleHelloAck(Envelope envelope) {
        HelloAck ack;
        try {
            ack = JsonCodec.decode(envelope.payload(), HelloAck.class);
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("HELLO_ACK 载荷非法: {}", e.getMessage());
            return;
        }

        serverProtocol = ack.protocol();
        MineUiClient.LOGGER.info("已连接 MineUI 服务端: protocol={} server={} minClient={}",
                ack.protocol(), ack.serverVersion(), ack.minimumClient());

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "[MineUI] 已连接服务端 v" + ack.serverVersion()
                            + "（protocol " + ack.protocol() + "）"));
        }
    }
}
