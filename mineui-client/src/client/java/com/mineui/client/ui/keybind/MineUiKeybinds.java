package com.mineui.client.ui.keybind;

import com.mineui.client.net.ProtocolClient;
import com.mineui.protocol.msg.Keybind;
import com.mineui.protocol.msg.Keybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端通用键位池：预注册 8 个槽位，服务端通过 KEYBIND 声明槽位对应的全局动作。
 * 按键注册/改键/冲突检测/持久化由原版按键系统提供。
 */
public final class MineUiKeybinds {

    public static final int SLOTS = 8;

    /** 槽位默认键：1/2 绑定 F7/F8，其余留空由玩家自行设置。 */
    private static final int[] DEFAULTS = {
            GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8,
            GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_UNKNOWN,
            GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_UNKNOWN,
            GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_UNKNOWN
    };

    private static final KeyMapping[] KEYS = new KeyMapping[SLOTS];
    /** 槽位（"1".."8"）→ 声明。 */
    private static final Map<String, Keybind> ASSIGNMENTS = new ConcurrentHashMap<>();

    private MineUiKeybinds() {
    }

    /** 客户端初始化时调用（注册到原版按键系统）。 */
    public static void init() {
        for (int i = 0; i < SLOTS; i++) {
            KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.mineui.slot" + (i + 1), InputConstants.Type.KEYSYM, DEFAULTS[i], KeyMapping.Category.MISC));
        }
    }

    /** 每 tick 检查按键。 */
    public static void tick() {
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping key = KEYS[i];
            while (key.consumeClick()) {
                Keybind bind = ASSIGNMENTS.get(Integer.toString(i + 1));
                if (bind != null && !bind.action().isEmpty()) {
                    ProtocolClient.sendGlobalAction(bind.action());
                }
            }
        }
    }

    /** 应用服务端全量声明。 */
    public static void apply(Keybinds update) {
        ASSIGNMENTS.clear();
        if (update == null || update.keybinds() == null) {
            return;
        }
        for (Keybind bind : update.keybinds()) {
            if (bind != null && bind.slot() != null && !bind.slot().isEmpty()) {
                ASSIGNMENTS.put(bind.slot(), bind);
            }
        }
    }

    /** 断线/切服清理。 */
    public static void reset() {
        ASSIGNMENTS.clear();
    }

    /** 页面按键提示：{@code {key.<actionId>}} 解析为当前按键名（未声明/未绑定返回空串）。 */
    public static String display(String actionId) {
        if (actionId == null || actionId.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, Keybind> entry : ASSIGNMENTS.entrySet()) {
            if (actionId.equals(entry.getValue().action())) {
                int index = slotIndex(entry.getKey());
                if (index < 0) {
                    return "";
                }
                KeyMapping key = KEYS[index];
                return key.isUnbound() ? "" : key.getTranslatedKeyMessage().getString();
            }
        }
        return "";
    }

    private static int slotIndex(String slot) {
        try {
            int value = Integer.parseInt(slot.trim());
            return value >= 1 && value <= SLOTS ? value - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
