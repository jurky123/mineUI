package com.mineui.client;

import com.mineui.client.net.MineUiPayload;
import com.mineui.client.net.ProtocolClient;
import com.mineui.client.ui.hud.MineUiHuds;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MineUI 客户端入口：通道/握手、HUD 渲染层、本地偏好键位。 */
public class MineUiClient implements ClientModInitializer {

    public static final String MOD_ID = "mineui";
    public static final String MINECRAFT_VERSION = "26.2";
    public static final Logger LOGGER = LoggerFactory.getLogger("MineUI");

    private static KeyMapping toggleHudKey;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(MineUiPayload.TYPE, MineUiPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MineUiPayload.TYPE, MineUiPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(MineUiPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ProtocolClient.handleIncoming(payload.data())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ProtocolClient.sendHello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ProtocolClient.reset());

        // HUD 渲染层（服务端声明布局，本地偏好可覆盖）
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, deltaTracker) -> MineUiHuds.render(graphics, Minecraft.getInstance().font));

        // 本地 HUD 总开关（F6，可在原版按键设置里改键；不影响服务端）
        toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mineui.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.consumeClick()) {
                MineUiConfig config = MineUiConfig.get();
                config.hudEnabled = !config.hudEnabled;
                config.save();
                var player = client.player;
                if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "[MineUI] HUD 已" + (config.hudEnabled ? "开启" : "关闭") + "（F6 切换）"));
                }
            }
        });

        LOGGER.info("MineUI client {} 初始化完成", version());
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
