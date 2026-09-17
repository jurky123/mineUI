package com.mineui.client.ui.render;

import com.mineui.client.MineUiClient;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.PlayerViewNode;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 3D 预览用的假实体（不真正进世界）。
 * 实体在渲染线程惰性创建并按节点缓存。
 */
public final class EntityPreviews {

    private static final Map<EntityViewNode, LivingEntity> ENTITIES = new WeakHashMap<>();
    private static final Map<PlayerViewNode, AbstractClientPlayer> PLAYERS = new WeakHashMap<>();

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

    public static AbstractClientPlayer player(PlayerViewNode node) {
        AbstractClientPlayer cached = PLAYERS.get(node);
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        String name = node.player();
        if (name.equals("@self")) {
            AbstractClientPlayer self = minecraft.player;
            if (self != null) {
                PLAYERS.put(node, self);
            }
            return self;
        }

        AbstractClientPlayer result = null;
        for (AbstractClientPlayer player : level.players()) {
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                result = player;
                break;
            }
        }
        if (result == null && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfoIgnoreCase(name);
            if (info != null && info.getProfile().name() != null) {
                RemotePlayer remote = new RemotePlayer(level, info.getProfile());
                injectPlayerInfo(remote, info);
                place(remote);
                remote.setOldPosAndRot();
                result = remote;
            }
        }
        if (result == null) {
            MineUiClient.LOGGER.warn("PlayerView 找不到在线玩家: {}", name);
            return null;
        }
        PLAYERS.put(node, result);
        return result;
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
