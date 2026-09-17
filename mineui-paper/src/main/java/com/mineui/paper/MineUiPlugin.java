package com.mineui.paper;

import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.HelloAck;
import com.mineui.paper.command.MineUiCommand;
import com.mineui.paper.ui.UiSessionManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * MineUI 服务端核心（Phase 0：通道 + 握手）。
 * <p>
 * 第一原则：Server owns state. Client owns presentation.
 */
public final class MineUiPlugin extends JavaPlugin implements PluginMessageListener {

    /** 唯一底层通道；业务用消息内 namespace 区分。 */
    public static final String CHANNEL = "mineui:main";

    /** 要求的客户端最低版本（能力协商预留）。 */
    public static final String MIN_CLIENT_VERSION = "0.1.0";

    private static final long BAD_PACKET_WARN_INTERVAL_MILLIS = 5000L;

    private SessionManager sessions;
    private UiSessionManager uiSessions;
    private Transport transport;
    private volatile long lastBadPacketWarn;

    @Override
    public void onEnable() {
        sessions = new SessionManager();
        uiSessions = new UiSessionManager(this);
        transport = new Transport(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        MineUiCommand command = new MineUiCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> command.register(event.registrar()));

        getLogger().info("MineUI " + getPluginMeta().getVersion() + " enabled (channel " + CHANNEL + ")");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        if (sessions != null) {
            sessions.clear();
        }
        if (uiSessions != null) {
            uiSessions.clear();
        }
    }

    /** 注意：回调运行在 Netty 线程，禁止直接触碰 Bukkit API（发送用 Transport#sendLater）。 */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        Envelope envelope;
        try {
            envelope = Envelope.decode(message);
        } catch (ProtocolException e) {
            warnBadPacket(player, e.getMessage());
            return;
        }

        switch (envelope.type()) {
            case HELLO -> handleHello(player, envelope);
            case ACTION -> Bukkit.getScheduler().runTask(this,
                    () -> uiSessions.handleAction(player, envelope));
            case CLOSE -> Bukkit.getScheduler().runTask(this,
                    () -> uiSessions.handleClose(player, envelope.session()));
            case PING -> transport.sendLater(player,
                    new Envelope(MessageType.PONG, envelope.session(), envelope.revision(), envelope.payload()));
            default -> getLogger().fine(() -> "忽略消息 " + envelope.type() + "（" + player.getName() + "）");
        }
    }

    private void handleHello(Player player, Envelope envelope) {
        Hello hello;
        try {
            hello = JsonCodec.decode(envelope.payload(), Hello.class);
        } catch (ProtocolException e) {
            warnBadPacket(player, "HELLO 载荷非法: " + e.getMessage());
            return;
        }

        if (hello.protocol() != Envelope.PROTOCOL_VERSION) {
            getLogger().warning(player.getName() + " MineUI 协议版本不匹配: " + hello.protocol()
                    + "（服务端 " + Envelope.PROTOCOL_VERSION + "），已忽略握手");
            return;
        }

        sessions.register(player.getUniqueId(), new SessionManager.ClientSession(
                hello.protocol(), hello.modVersion(), hello.minecraft(),
                hello.capabilities(), System.currentTimeMillis()));

        getLogger().info(player.getName() + " MineUI " + hello.modVersion() + " connected"
                + " (protocol " + hello.protocol() + ", mc " + hello.minecraft()
                + ", caps=" + hello.capabilities() + ")");

        HelloAck ack = new HelloAck(Envelope.PROTOCOL_VERSION, getPluginMeta().getVersion(), MIN_CLIENT_VERSION);
        transport.sendLater(player, new Envelope(MessageType.HELLO_ACK, 0, 0, JsonCodec.encode(ack)));
    }

    private void warnBadPacket(Player player, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastBadPacketWarn >= BAD_PACKET_WARN_INTERVAL_MILLIS) {
            lastBadPacketWarn = now;
            getLogger().warning("来自 " + player.getName() + " 的非法 MineUI 包: " + reason);
        } else {
            getLogger().fine(() -> "来自 " + player.getName() + " 的非法 MineUI 包: " + reason);
        }
    }

    public SessionManager sessions() {
        return sessions;
    }

    public UiSessionManager uiSessions() {
        return uiSessions;
    }

    public Transport transport() {
        return transport;
    }
}
