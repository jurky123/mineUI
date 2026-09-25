package com.mineui.client.ui.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.client.MineUiClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对 {@code mineui-client-api} 的安全封装（api mod 缺失时惰性降级）。
 * <p>
 * 页面可通过 {@code {local.<ns>.<key>}} 读取本地状态、{@code local:<ns>.<action>} 直连本地动作；
 * 命名空间隔离：动作只派发到同命名空间的注册者。
 */
public final class MineUiLocalBridge {

    private static final String LOCAL_PREFIX = "local:";
    private static final String LOCAL_BINDING_PREFIX = "local.";

    private MineUiLocalBridge() {
    }

    /** 是否 local: 动作。 */
    public static boolean isLocalAction(String actionId) {
        return actionId != null && actionId.startsWith(LOCAL_PREFIX);
    }

    /** 解析绑定路径（传入 {@code "local."} 之后的部分，如 {@code "mineaudio.position"}）。 */
    public static String state(String path, String fallback) {
        int dot = path.indexOf('.');
        String namespace = dot < 0 ? path : path.substring(0, dot);
        String key = dot < 0 ? "" : path.substring(dot + 1);
        Object value = rawState(namespace, key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 派发 {@code local:<ns>.<action>}（传入 {@code "local:"} 之后的部分）。 */
    public static boolean dispatch(String localAction, JsonObject payload) {
        int dot = localAction.indexOf('.');
        if (dot <= 0 || dot >= localAction.length() - 1) {
            return false;
        }
        String namespace = localAction.substring(0, dot);
        String action = localAction.substring(dot + 1);
        try {
            return com.mineui.client.api.MineUiClientRegistry.dispatchAction(namespace, action, toMap(payload));
        } catch (Throwable t) {
            MineUiClient.LOGGER.debug("本地动作不可用（{}）: {}", localAction, t.toString());
            return false;
        }
    }

    /** 汇总随 HELLO 上报的本地能力位（含业务声明的能力）。 */
    public static List<String> capabilities() {
        List<String> caps = new ArrayList<>();
        try {
            if (com.mineui.client.api.MineUiClientRegistry.hasStateProviders()) {
                caps.add(com.mineui.client.api.MineUiClientRegistry.CAPABILITY_LOCAL_STATE);
            }
            if (com.mineui.client.api.MineUiClientRegistry.hasActionHandlers()) {
                caps.add(com.mineui.client.api.MineUiClientRegistry.CAPABILITY_LOCAL_ACTION);
            }
            if (com.mineui.client.api.MineUiClientRegistry.hasImageProviders()) {
                caps.add(com.mineui.client.api.MineUiClientRegistry.CAPABILITY_LOCAL_IMAGE);
            }
            caps.addAll(com.mineui.client.api.MineUiClientRegistry.declaredCapabilities());
        } catch (Throwable t) {
            // mineui-client-api 未安装：无本地能力
        }
        return caps;
    }

    /**
     * 本地结构读取（列表/对象绑定用）：把本地值转成 JSON 树。
     * 支持 List / Map / String / Number / Boolean / 已有 JsonElement。
     */
    public static com.google.gson.JsonElement element(String path) {
        int dot = path.indexOf('.');
        String namespace = dot < 0 ? path : path.substring(0, dot);
        String key = dot < 0 ? "" : path.substring(dot + 1);
        Object value = rawState(namespace, key);
        if (value == null) {
            return null;
        }
        if (value instanceof com.google.gson.JsonElement json) {
            return json;
        }
        try {
            return com.google.gson.JsonParser.parseString(com.mineui.protocol.JsonCodec.toJson(value));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 本地结构代数：变化时应触发页面重排（纯数值变化不会）。 */
    public static long generation() {
        try {
            return com.mineui.client.api.MineUiClientRegistry.generation();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** 某个命名空间的结构代数（FR-19 本地图片缓存失效用）。 */
    public static long namespaceGeneration(String namespace) {
        try {
            return com.mineui.client.api.MineUiClientRegistry.generation(namespace);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** 读取本地图字节（传入 {@code "local."} 之后的部分，如 {@code "mineaudio.lib0_cover"}）。 */
    public static byte[] image(String path) {
        int dot = path == null ? -1 : path.indexOf('.');
        if (dot <= 0 || dot >= path.length() - 1) {
            return null;
        }
        String namespace = path.substring(0, dot);
        String key = path.substring(dot + 1);
        try {
            return com.mineui.client.api.MineUiClientRegistry.getImage(namespace, key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object rawState(String namespace, String key) {
        try {
            return com.mineui.client.api.MineUiClientRegistry.getState(namespace, key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<String, Object> toMap(JsonObject payload) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (payload == null) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                map.put(entry.getKey(), null);
            } else if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    map.put(entry.getKey(), primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    map.put(entry.getKey(), primitive.getAsDouble());
                } else {
                    map.put(entry.getKey(), primitive.getAsString());
                }
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }
}
