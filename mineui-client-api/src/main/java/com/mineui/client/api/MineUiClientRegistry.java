package com.mineui.client.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地状态/动作注册表（纯 Java，无 MC 依赖）。
 * <p>
 * 业务客户端通过 {@link MineUiClientBridge#get()} 注册；MineUI 客户端读取/派发。
 * 线程安全：注册发生在客户端初始化，读取在渲染线程。
 */
public final class MineUiClientRegistry {

    /** 能力位：存在本地状态提供者。 */
    public static final String CAPABILITY_LOCAL_STATE = "local_state";
    /** 能力位：存在本地动作处理器。 */
    public static final String CAPABILITY_LOCAL_ACTION = "local_action";

    private record Entry(ClientStateProvider state, ClientActionHandler actions) {
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Set<String> CAPABILITIES = ConcurrentHashMap.newKeySet();
    /** 注册表版本：注册/注销时自增，保证结构变化能触发页面重排。 */
    private static final java.util.concurrent.atomic.AtomicLong VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    private static final MineUiClientBridge BRIDGE = new MineUiClientBridge() {
        @Override
        public AutoCloseable register(String namespace, ClientStateProvider state, ClientActionHandler actions) {
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalArgumentException("namespace 不能为空");
            }
            Entry entry = new Entry(state, actions);
            ENTRIES.put(namespace, entry);
            VERSION.incrementAndGet();
            return () -> {
                if (ENTRIES.remove(namespace, entry)) {
                    VERSION.incrementAndGet();
                }
            };
        }

        @Override
        public void declareCapability(String capability) {
            if (capability != null && !capability.isBlank()) {
                CAPABILITIES.add(capability);
            }
        }
    };

    private MineUiClientRegistry() {
    }

    public static MineUiClientBridge bridge() {
        return BRIDGE;
    }

    /** 读取 {@code local.<namespace>.<key>}；未注册/无值/异常返回 null。 */
    public static Object getState(String namespace, String key) {
        Entry entry = ENTRIES.get(namespace);
        if (entry == null || entry.state() == null) {
            return null;
        }
        try {
            return entry.state().get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 派发同命名空间动作；未注册/未处理/异常返回 false。 */
    public static boolean dispatchAction(String namespace, String action, Map<String, Object> payload) {
        Entry entry = ENTRIES.get(namespace);
        if (entry == null || entry.actions() == null) {
            return false;
        }
        try {
            return entry.actions().handle(action, payload == null ? Collections.emptyMap() : payload);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 汇总结构代数：注册表版本（注册/注销）+ 各提供者的 {@link ClientStateProvider#generation()}。
     * 任一变化都会反映出来，用于触发页面重排。
     */
    public static long generation() {
        long total = VERSION.get();
        for (Entry entry : ENTRIES.values()) {
            if (entry.state() == null) {
                continue;
            }
            try {
                total += entry.state().generation();
            } catch (RuntimeException e) {
                // 忽略：不影响其他提供者
            }
        }
        return total;
    }

    public static boolean hasStateProviders() {
        return ENTRIES.values().stream().anyMatch(entry -> entry.state() != null);
    }

    public static boolean hasActionHandlers() {
        return ENTRIES.values().stream().anyMatch(entry -> entry.actions() != null);
    }

    public static Set<String> declaredCapabilities() {
        return Set.copyOf(CAPABILITIES);
    }

    /** 测试用：清空注册表。 */
    public static void clear() {
        ENTRIES.clear();
        CAPABILITIES.clear();
    }
}
