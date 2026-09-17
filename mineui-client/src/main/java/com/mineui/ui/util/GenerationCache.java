package com.mineui.ui.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 按状态代数（generation）失效的缓存：同一代数内每个 key 只解析一次。
 * 用于把 {@code {state.x}} 模板解析成贴图 id / 物品 id 等，避免每帧重复解析。
 */
public final class GenerationCache<V> {

    private int generation = Integer.MIN_VALUE;
    private final Map<String, V> cache = new HashMap<>();

    /** 取缓存；代数变化时整体清空。miss 时调用 resolver 并写入。 */
    public V get(int currentGeneration, String key, Function<String, V> resolver) {
        if (currentGeneration != generation) {
            cache.clear();
            generation = currentGeneration;
        }
        V value = cache.get(key);
        if (value == null) {
            value = resolver.apply(key);
            cache.put(key, value);
        }
        return value;
    }

    public int size() {
        return cache.size();
    }
}
