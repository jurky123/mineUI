package com.mineui.client;

import com.mineui.client.net.MineUiPayload;
import com.mineui.client.net.ProtocolClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MineUI 客户端入口（Phase 0：注册通道 + 加入时握手）。 */
public class MineUiClient implements ClientModInitializer {

    public static final String MOD_ID = "mineui";
    public static final String MINECRAFT_VERSION = "26.2";
    public static final Logger LOGGER = LoggerFactory.getLogger("MineUI");

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(MineUiPayload.TYPE, MineUiPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MineUiPayload.TYPE, MineUiPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(MineUiPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ProtocolClient.handleIncoming(payload.data())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ProtocolClient.sendHello());

        LOGGER.info("MineUI client {} 初始化完成", version());
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
