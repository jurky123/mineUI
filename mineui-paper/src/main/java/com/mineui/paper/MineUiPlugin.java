package com.mineui.paper;

import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.MessageType;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.RemoteImagePolicy;
import com.mineui.protocol.msg.HelloAck;
import com.mineui.api.MineUi;
import com.mineui.api.MineUiProvider;
import com.mineui.paper.api.PaperMineUi;
import com.mineui.protocol.msg.Toast;
import com.mineui.paper.command.MineUiCommand;
import com.mineui.paper.ui.UiSessionManager;
import com.mineui.protocol.session.RateWindow;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * MineUI 服务端核心（Phase 0：通道 + 握手）。
 * <p>
 * 第一原则：Server owns state. Client owns presentation.
 */
public final class MineUiPlugin extends JavaPlugin implements PluginMessageListener, Listener {

    /** 唯一底层通道；业务用消息内 namespace 区分。 */
    public static final String CHANNEL = "mineui:main";

    /** 要求的客户端最低版本（能力协商预留）。 */
    public static final String MIN_CLIENT_VERSION = "0.1.0";

    private static final long BAD_PACKET_WARN_INTERVAL_MILLIS = 5000L;
    /** 网络入口限流：每玩家每秒最多处理的 MineUI 包数（在调度到主线程之前生效）。 */
    private static final int MAX_INBOUND_PACKETS_PER_SECOND = 30;

    private SessionManager sessions;
    private UiSessionManager uiSessions;
    private GlobalActionManager globalActions;
    private KeybindManager keybinds;
    private RemoteImagePolicy remoteImagePolicy = RemoteImagePolicy.disabled();
    private Transport transport;
    private final Map<UUID, RateWindow> inboundWindows = new ConcurrentHashMap<>();
    private volatile long lastBadPacketWarn;

    @Override
    public void onEnable() {
        sessions = new SessionManager();
        uiSessions = new UiSessionManager(this);
        globalActions = new GlobalActionManager(this);
        keybinds = new KeybindManager(this);
        transport = new Transport(this);
        loadRemoteImagePolicy();

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        MineUiProvider.register(new PaperMineUi(this));

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
        if (globalActions != null) {
            globalActions.clear();
        }
        if (keybinds != null) {
            keybinds.clear();
        }
        inboundWindows.clear();
        MineUiProvider.unregister();
    }

    /** 业务插件停用/重载时关闭它拥有的界面会话与全局动作（避免回调打到已卸载的插件代码）。 */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) {
            return;
        }
        if (uiSessions != null) {
            uiSessions.closeOwned(event.getPlugin());
        }
        if (globalActions != null) {
            globalActions.clearOwned(event.getPlugin());
        }
        if (keybinds != null) {
            keybinds.clearOwned(event.getPlugin());
        }
    }

    /** 注意：回调运行在 Netty 线程，禁止直接触碰 Bukkit API（发送用 Transport#sendLater）。 */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        if (!allowInbound(player)) {
            warnBadPacket(player, "包速率超限，已丢弃");
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
            case ACTION -> {
                if (envelope.session() == 0) {
                    // 全局动作（Toast 点击 / 键位等）：无会话
                    Bukkit.getScheduler().runTask(this, () -> globalActions.handle(player, envelope));
                } else {
                    Bukkit.getScheduler().runTask(this, () -> uiSessions.handleAction(player, envelope));
                }
            }
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
        transport.sendLater(player, new Envelope(MessageType.REMOTE_POLICY, 0, 0, JsonCodec.encode(remoteImagePolicy)));
        // 键位声明在握手应答后重发（客户端能力需已登记）
        Bukkit.getScheduler().runTask(this, () -> keybinds.sendTo(player));
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

    private boolean allowInbound(Player player) {
        return inboundWindows.computeIfAbsent(player.getUniqueId(),
                ignored -> new RateWindow(MAX_INBOUND_PACKETS_PER_SECOND, 1000L)).tryAcquire();
    }

    /** 玩家退出时清理入口限流状态。 */
    public void clearInbound(UUID playerId) {
        inboundWindows.remove(playerId);
    }

    private void loadRemoteImagePolicy() {
        saveDefaultConfig();
        var domains = getConfig().getStringList("remote-images.allowed-domains").stream()
                .map(String::trim)
                .filter(domain -> !domain.isEmpty())
                .toList();
        boolean enabled = getConfig().getBoolean("remote-images.enabled", true) && !domains.isEmpty();
        long maxBytes = getConfig().getLong("remote-images.max-bytes", 1024 * 1024);
        long cacheBytes = getConfig().getLong("remote-images.cache-bytes", 64L * 1024 * 1024);
        remoteImagePolicy = new RemoteImagePolicy(enabled, domains, maxBytes, cacheBytes);
        getLogger().info("远程图片: " + (enabled ? "开启（" + domains.size() + " 个域名）" : "关闭"));
    }

    public RemoteImagePolicy remoteImagePolicy() {
        return remoteImagePolicy;
    }

    /**
     * 下发 Toast（仅支持该能力的客户端）。
     * 必须在主线程调用；不需要会话，适合切歌/错误等短提示。
     */
    public void sendToast(Player player, Toast toast) {
        SessionManager.ClientSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.capabilities().contains(MineUi.CAPABILITY_TOAST)) {
            return;
        }
        transport.sendNow(player, new Envelope(MessageType.TOAST, 0, 0, JsonCodec.encode(toast)));
    }

    public GlobalActionManager globalActions() {
        return globalActions;
    }

    public KeybindManager keybinds() {
        return keybinds;
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
