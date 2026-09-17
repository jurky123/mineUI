package com.mineui.client.ui.render;

import com.google.common.collect.ImmutableMultimap;
import com.mineui.client.MineUiClient;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.PlayerViewNode;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 3D 预览用的假实体（不真正进世界）。
 * 实体在渲染线程惰性创建并按节点缓存；玩家预览的皮肤/名字变化时会重建缓存。
 */
public final class EntityPreviews {

    private static final Map<EntityViewNode, LivingEntity> ENTITIES = new WeakHashMap<>();
    private static final Map<PlayerViewNode, CachedPlayer> PLAYERS = new WeakHashMap<>();

    /** GameProfile 纹理属性名。 */
    private static final String TEXTURES = "textures";

    private record CachedPlayer(String key, AbstractClientPlayer player) {
    }

    private EntityPreviews() {
    }

    public static LivingEntity entity(EntityViewNode node) {
        LivingEntity cached = ENTITIES.get(node);
        if (cached != null) {
            return cached;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(node.entityType());
        if (id == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            MineUiClient.LOGGER.warn("EntityView 仅支持生物实体: {}", node.entityType());
            return null;
        }
        place(living);
        living.setYRot(node.yaw());
        living.setXRot(node.pitch());
        living.setYBodyRot(node.bodyYaw());
        living.setYHeadRot(node.bodyYaw());
        living.setOldPosAndRot();
        ENTITIES.put(node, living);
        return living;
    }

    /**
     * 玩家预览实体。
     *
     * @param node              节点（决定 {@code @self}/在线玩家名）
     * @param resolvedSkinValue 已解析的皮肤 value（null 表示按玩家名取皮）
     * @param signature         已解析的皮肤 signature（可空）
     */
    public static AbstractClientPlayer player(PlayerViewNode node,
                                              String resolvedSkinValue, String signature) {
        String key = resolvedSkinValue == null
                ? "name:" + node.player()
                : "skin:" + resolvedSkinValue + "|" + (signature == null ? "" : signature);
        CachedPlayer cached = PLAYERS.get(node);
        if (cached != null && cached.key().equals(key)) {
            return cached.player();
        }

        AbstractClientPlayer created = resolvedSkinValue == null
                ? byName(node.player())
                : byProperty(resolvedSkinValue, signature);
        if (created == null) {
            return null;
        }
        PLAYERS.put(node, new CachedPlayer(key, created));
        return created;
    }

    private static AbstractClientPlayer byName(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        if (name.equals("@self")) {
            return minecraft.player;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                return player;
            }
        }
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfoIgnoreCase(name);
            if (info != null && info.getProfile().name() != null) {
                return fromProfile(info.getProfile());
            }
        }
        MineUiClient.LOGGER.warn("PlayerView 找不到在线玩家: {}", name);
        return null;
    }

    /** 用皮肤 value/signature 构造一个离线假玩家，皮肤由原版 SkinManager 异步加载。 */
    private static AbstractClientPlayer byProperty(String value, String signature) {
        UUID id = UUID.nameUUIDFromBytes(("mineui:" + value).getBytes(StandardCharsets.UTF_8));
        // authlib 9 的 PropertyMap 是 ImmutableMultimap.copyOf，只能构造时带上纹理属性，不能后置 put
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of(
                TEXTURES, new Property(TEXTURES, value, signature)));
        GameProfile profile = new GameProfile(id, "MineUI", properties);
        return fromProfile(profile);
    }

    private static AbstractClientPlayer fromProfile(GameProfile profile) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        RemotePlayer remote = new RemotePlayer(level, profile);
        injectPlayerInfo(remote, new PlayerInfo(profile, false));
        place(remote);
        remote.setOldPosAndRot();
        return remote;
    }

    private static void place(Entity entity) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            entity.setPos(player.getX(), player.getY(), player.getZ());
        }
    }

    /**
     * 把玩家列表条目注入假玩家，使其使用正确的皮肤（26.x 无映射表，AW 编译期不可用，故用反射）。
     */
    private static void injectPlayerInfo(RemotePlayer remote, PlayerInfo info) {
        try {
            PLAYER_INFO_FIELD.set(remote, info);
        } catch (ReflectiveOperationException e) {
            MineUiClient.LOGGER.warn("注入玩家皮肤信息失败: {}", e.getMessage());
        }
    }

    private static final java.lang.reflect.Field PLAYER_INFO_FIELD = resolvePlayerInfoField();

    private static java.lang.reflect.Field resolvePlayerInfoField() {
        try {
            java.lang.reflect.Field field = AbstractClientPlayer.class.getDeclaredField("playerInfo");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("找不到 AbstractClientPlayer.playerInfo", e);
        }
    }
}
