package com.mineui.client.net;

import com.google.gson.JsonObject;
import com.mineui.client.MineUiClient;
import com.mineui.client.ui.MineUiScreens;
import com.mineui.client.ui.hud.MineUiHuds;
import com.mineui.client.ui.keybind.MineUiKeybinds;
import com.mineui.client.ui.local.MineUiLocalBridge;
import com.mineui.client.ui.remote.RemoteImages;
import com.mineui.client.ui.toast.MineUiToasts;
import com.mineui.client.ui.UiStateStore;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Action;
import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.HelloAck;
import com.mineui.protocol.msg.Keybinds;
import com.mineui.protocol.msg.Open;
import com.mineui.protocol.msg.RemoteImagePolicy;
import com.mineui.protocol.msg.Patch;
import com.mineui.protocol.msg.Snapshot;
import com.mineui.protocol.msg.Toast;
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

    /** 握手未完成时的重试间隔（tick）与次数上限。 */
    private static final int HELLO_RETRY_TICKS = 40;
    private static final int MAX_HELLO_RETRIES = 3;

    private static volatile int serverProtocol = -1;
    private static int helloRetries;
    private static int ticksSinceHello;

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

    /** 进入服务器时开始握手。 */
    public static void onJoin() {
        helloRetries = 0;
        ticksSinceHello = 0;
        sendHello();
    }

    public static void sendHello() {
        List<String> capabilities = new java.util.ArrayList<>(
                List.of("screen", "hud", "server_ui", "hud_v2", "remote_image", "toast", "keybind"));
        for (String capability : MineUiLocalBridge.capabilities()) {
            if (!capabilities.contains(capability)) {
                capabilities.add(capability);
            }
        }
        Hello hello = new Hello(
                Envelope.PROTOCOL_VERSION,
                MineUiClient.version(),
                MineUiClient.MINECRAFT_VERSION,
                capabilities);
        send(new Envelope(MessageType.HELLO, 0, 0, JsonCodec.encode(hello)));
    }

    /** 客户端 tick：加载期发出的握手响应可能丢失，未收到 ACK 时定时重试（仅服务端注册了通道时）。 */
    public static void tick() {
        if (serverProtocol >= 0) {
            return;
        }
        ticksSinceHello++;
        if (ticksSinceHello < HELLO_RETRY_TICKS || helloRetries >= MAX_HELLO_RETRIES) {
            return;
        }
        ticksSinceHello = 0;
        helloRetries++;
        if (ClientPlayNetworking.canSend(MineUiPayload.ID)) {
            MineUiClient.LOGGER.info("尚未收到 HELLO_ACK，重试握手（第 {} 次）", helloRetries);
            sendHello();
        }
    }

    public static void sendAction(String actionId) {
        sendAction(actionId, null);
    }

    /** 发送带负载的动作（如输入框提交 {@code {"text": ...}}）；{@code local:} 动作本地直连、不回传。 */
    public static void sendAction(String actionId, JsonObject payload) {
        if (MineUiLocalBridge.isLocalAction(actionId)) {
            if (!MineUiLocalBridge.dispatch(actionId.substring("local:".length()), payload)) {
                MineUiClient.LOGGER.debug("本地动作未处理，已忽略: {}", actionId);
            }
            return;
        }
        if (!STATE.active()) {
            MineUiClient.LOGGER.warn("无活动会话，忽略动作 {}", actionId);
            return;
        }
        Action action = new Action(actionId, payload);
        send(new Envelope(MessageType.ACTION, STATE.session(), STATE.revision(), JsonCodec.encode(action)));
    }

    /** 全局动作（无会话，Toast 点击 / 键位等）：session=0，由服务端全局处理器接收。 */
    public static void sendGlobalAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) {
            return;
        }
        send(new Envelope(MessageType.ACTION, 0, 0, JsonCodec.encode(new Action(actionId, null))));
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

        if (MineUiHuds.handle(envelope)) {
            return;
        }

        switch (envelope.type()) {
            case HELLO_ACK -> handleHelloAck(envelope);
            case REMOTE_POLICY -> handleRemotePolicy(envelope);
            case TOAST -> handleToast(envelope);
            case KEYBIND -> handleKeybinds(envelope);
            case OPEN -> handleOpen(envelope);
            case SNAPSHOT -> handleSnapshot(envelope);
            case PATCH -> handlePatch(envelope);
            case CLOSE -> {
                if (isCurrentSession(envelope)) {
                    handleServerClose();
                }
            }
            case PING -> send(new Envelope(MessageType.PONG, envelope.session(), envelope.revision(), envelope.payload()));
            default -> MineUiClient.LOGGER.debug("忽略消息 {}", envelope.type());
        }
    }

    private static void handleRemotePolicy(Envelope envelope) {
        try {
            RemoteImages.setPolicy(JsonCodec.decode(envelope.payload(), RemoteImagePolicy.class));
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("REMOTE_POLICY 载荷非法: {}", e.getMessage());
        }
    }

    private static void handleToast(Envelope envelope) {
        try {
            MineUiToasts.show(JsonCodec.decode(envelope.payload(), Toast.class));
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("TOAST 载荷非法: {}", e.getMessage());
        }
    }

    private static void handleKeybinds(Envelope envelope) {
        try {
            MineUiKeybinds.apply(JsonCodec.decode(envelope.payload(), Keybinds.class));
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("KEYBIND 载荷非法: {}", e.getMessage());
        }
    }

    /** 断线/切服清理：状态、界面、图片句柄、协商版本全部复位。 */
    public static void reset() {
        STATE.end();
        MineUiScreens.closeFromServer();
        MineUiHuds.reset();
        RemoteImages.reset();
        MineUiToasts.reset();
        MineUiKeybinds.reset();
        serverProtocol = -1;
        helloRetries = 0;
        ticksSinceHello = 0;
        MineUiClient.LOGGER.info("已断开连接，MineUI 状态已清理");
    }

    private static boolean isCurrentSession(Envelope envelope) {
        if (!STATE.active() || envelope.session() != STATE.session()) {
            MineUiClient.LOGGER.debug("忽略非当前会话的 {}（收到 {}，当前 {}）",
                    envelope.type(), envelope.session(), STATE.session());
            return false;
        }
        return true;
    }

    private static void handleOpen(Envelope envelope) {
        Open open;
        try {
            open = JsonCodec.decode(envelope.payload(), Open.class);
        } catch (ProtocolException e) {
            MineUiClient.LOGGER.warn("OPEN 载荷非法: {}", e.getMessage());
            return;
        }
        if (open.isHud()) {
            MineUiHuds.open(envelope.session(), open.app(), open.view(), open.ui(),
                    open.layout() == null ? com.mineui.protocol.msg.HudLayout.defaults() : open.layout());
            return;
        }
        STATE.begin(envelope.session());
        MineUiScreens.open(open.app(), open.view(), open.ui());
        MineUiClient.LOGGER.info("打开界面 {} / {} (session {}, ui={})", open.app(), open.view(),
                envelope.session(), open.ui() == null ? "内置" : "服务端下发");
    }

    private static void handleSnapshot(Envelope envelope) {
        if (!isCurrentSession(envelope)) {
            return;
        }
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
        if (!isCurrentSession(envelope)) {
            return;
        }
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

        boolean firstAck = serverProtocol < 0;
        serverProtocol = ack.protocol();
        if (!firstAck) {
            MineUiClient.LOGGER.debug("忽略重复的 HELLO_ACK（protocol {}）", ack.protocol());
            return;
        }
        MineUiClient.LOGGER.info("已连接 MineUI 服务端: protocol={} server={} minClient={}",
                ack.protocol(), ack.serverVersion(), ack.minimumClient());

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "[MineUI] 已连接服务端 v" + ack.serverVersion()
                            + "（protocol " + ack.protocol() + "）· 客户端 v" + MineUiClient.version()));
        }
    }
}
