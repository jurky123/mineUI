package com.mineui.client.net;

import com.mineui.client.MineUiClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 唯一的 MineUI 底层载荷：内部承载 {@code com.mineui.protocol.Envelope} 字节。 */
public record MineUiPayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(MineUiClient.MOD_ID, "main");

    public static final CustomPacketPayload.Type<MineUiPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, MineUiPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data()),
            buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new MineUiPayload(data);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
