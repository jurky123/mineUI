package com.mineui.client.ui.hud;

import com.google.gson.JsonObject;
import com.mineui.client.MineUiConfig;
import com.mineui.client.MineUiClient;
import com.mineui.client.ui.UiDefinitions;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.HudLayout;
import com.mineui.protocol.msg.Patch;
import com.mineui.protocol.msg.Snapshot;
import com.mineui.ui.spec.UiDefinition;
import com.mineui.ui.spec.UiSpecException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 客户端 HUD 会话注册与渲染（显示型；不占用屏幕）。 */
public final class MineUiHuds {

    private static final Map<Integer, HudSession> SESSIONS = new LinkedHashMap<>();

    private MineUiHuds() {
    }

    /** 服务端 OPEN(mode=hud)：加载定义并注册 HUD。 */
    public static void open(int sessionId, String app, String view, JsonObject ui, HudLayout layout) {
        try {
            UiDefinition definition = UiDefinitions.load(app, view, ui);
            HudSession session = new HudSession(sessionId, app, view, definition.source(), definition.root(), layout);
            session.state().begin(sessionId);
            SESSIONS.put(sessionId, session);
            MineUiClient.LOGGER.info("打开 HUD {} / {}（来源: {}，session {}）", app, view, definition.source(), sessionId);
        } catch (UiSpecException e) {
            MineUiClient.LOGGER.warn("HUD 定义加载失败 {} / {}: {}", app, view, e.getMessage());
            chat("[MineUI] HUD 定义加载失败: " + app + "/" + view);
        }
    }

    /** 处理 HUD 会话的 SNAPSHOT/PATCH/CLOSE；非 HUD 会话返回 false。 */
    public static boolean handle(Envelope envelope) {
        if (envelope.type() != com.mineui.protocol.MessageType.SNAPSHOT
                && envelope.type() != com.mineui.protocol.MessageType.PATCH
                && envelope.type() != com.mineui.protocol.MessageType.CLOSE) {
            return false;
        }
        HudSession session = SESSIONS.get(envelope.session());
        if (session == null) {
            return false;
        }
        switch (envelope.type()) {
            case SNAPSHOT -> {
                try {
                    Snapshot snapshot = JsonCodec.decode(envelope.payload(), Snapshot.class);
                    session.state().applySnapshot(envelope.revision(), snapshot.state());
                } catch (ProtocolException e) {
                    MineUiClient.LOGGER.warn("HUD SNAPSHOT 载荷非法: {}", e.getMessage());
                }
            }
            case PATCH -> {
                try {
                    Patch patch = JsonCodec.decode(envelope.payload(), Patch.class);
                    session.state().applyPatch(envelope.revision(), patch.ops());
                } catch (ProtocolException e) {
                    MineUiClient.LOGGER.warn("HUD PATCH 应用失败: {}", e.getMessage());
                }
            }
            case CLOSE -> SESSIONS.remove(envelope.session());
            default -> {
            }
        }
        return true;
    }

    /** HUD 渲染回调：逐个渲染启用中的 HUD。 */
    public static void render(GuiGraphicsExtractor graphics, Font font) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MineUiConfig config = MineUiConfig.get();
        for (HudSession session : new LinkedHashMap<>(SESSIONS).values()) {
            if (!config.isHudEnabled(session.app(), session.view())) {
                continue;
            }
            try {
                session.render(graphics, font);
            } catch (Exception e) {
                MineUiClient.LOGGER.warn("HUD 渲染失败 {} / {}: {}", session.app(), session.view(), e.getMessage());
            }
        }
    }

    /** 断线/切服清理。 */
    public static void reset() {
        SESSIONS.clear();
    }

    public static int count() {
        return SESSIONS.size();
    }

    private static void chat(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
