package com.mineui.client.net;

import com.mineui.client.MineUiClient;
import com.mineui.client.ui.MineUiScreens;
import com.mineui.client.ui.UiStateStore;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Action;
import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.HelloAck;
import com.mineui.protocol.msg.Open;
import com.mineui.protocol.msg.Patch;
import com.mineui.protocol.msg.Snapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 客户端协议层：握手、信封收发、界面会话路由。
 * 所有方法均在渲染线程调用（接收回调已由入口切线程）。
 */
public final class ProtocolClient {

    private static final UiStateStore STATE = new UiStateStore();

    private static volatile int serverProtocol = -1;

    private ProtocolClient() {
    }

    /** 服务端协商出的协议版本；-1 表示尚未握手成功。 */
    public static int serverProtocol() {
        return serverProtocol;
    }

    public static UiStateStore state() {
        return STATE;
    }

    // ---------- 发送 ----------

    public static void sendHello() {
        Hello hello = new Hello(
                Envelope.PROTOCOL_VERSION,
                MineUiClient.version(),
                MineUiClient.MINECRAFT_VERSION,
                List.of("screen", "hud"));
        send(new Envelope(MessageType.HELLO, 0, 0, JsonCodec.encode(hello)));
    }

    public static void sendAction(String actionId) {
        if (!STATE.active()) {
            MineUiClient.LOGGER.warn("无活动会话，忽略动作 {}", actionId);
            return;
        }
        Action action = new Action(actionId);
        send(new Envelope(MessageType.ACTION, STATE.session(), STATE.revision(), JsonCodec.encode(action)));
    }

    public static void sendClose() {
        if (!STATE.active()) {
            return;
        }
        send(new Envelope(MessageType.CLOSE, STATE.session(), STATE.revision(), new byte[0]));
        STATE.end();
    }

    public static void send(Envelope envelope) {
        if (!ClientPlayNetworking.canSend(MineUiPayload.ID)) {
            MineUiClient.LOGGER.warn("服务端未注册 {} 通道，丢弃 {}", MineUiPayload.ID, envelope.type());
            return;
        }
        ClientPlayNetworking.send(new MineUiPayload(envelope.encode()));
    }

    // ---------- 接收 ----------

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
            case OPEN -> handleOpen(envelope);
            case SNAPSHOT -> handleSnapshot(envelope);
            case PATCH -> handlePatch(envelope);
            case CLOSE -> handleServerClose();
            case PING -> send(new Envelope(MessageType.PONG, envelope.session(), envelope.revision(), envelope.payload()));
            default -> MineUiClient.LOGGER.debug("忽略消息 {}", envelope.type());
        }
    }

    private static void handleOpen(Envelope envelope) {
        Open open;
        try {
            open = JsonCodec.decode(envelope.payload(), Open.class);
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("OPEN 载荷非法: {}", e.getMessage());
            return;
        }
        STATE.begin(envelope.session());
        MineUiScreens.open(open.app(), open.view());
        MineUiClient.LOGGER.info("打开界面 {} / {} (session {})", open.app(), open.view(), envelope.session());
    }

    private static void handleSnapshot(Envelope envelope) {
        Snapshot snapshot;
        try {
            snapshot = JsonCodec.decode(envelope.payload(), Snapshot.class);
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("SNAPSHOT 载荷非法: {}", e.getMessage());
            return;
        }
        STATE.applySnapshot(envelope.revision(), snapshot.state());
    }

    private static void handlePatch(Envelope envelope) {
        Patch patch;
        try {
            patch = JsonCodec.decode(envelope.payload(), Patch.class);
            STATE.applyPatch(envelope.revision(), patch.ops());
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("PATCH 应用失败: {}", e.getMessage());
        }
    }

    private static void handleServerClose() {
        STATE.end();
        MineUiScreens.closeFromServer();
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
