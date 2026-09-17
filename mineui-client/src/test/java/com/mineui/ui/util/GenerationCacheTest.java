package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationCacheTest {

    @Test
    void resolvesOncePerGenerationPerKey() {
        GenerationCache<String> cache = new GenerationCache<>();
        AtomicInteger calls = new AtomicInteger();

        assertEquals("A", cache.get(1, "a", key -> {
            calls.incrementAndGet();
            return key.toUpperCase();
        }));
        assertEquals("A", cache.get(1, "a", key -> {
            calls.incrementAndGet();
            return key.toUpperCase();
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void invalidatesOnGenerationChange() {
        GenerationCache<String> cache = new GenerationCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.get(1, "a", key -> {
            calls.incrementAndGet();
            return "v1";
        });
        cache.get(2, "a", key -> {
            calls.incrementAndGet();
            return "v2";
        });
        assertEquals(2, calls.get());
        assertEquals(1, cache.size(), "换代后旧条目应被清空");
    }

    @Test
    void keepsDifferentKeysInSameGeneration() {
        GenerationCache<String> cache = new GenerationCache<>();
        assertEquals("a", cache.get(1, "a", k -> k));
        assertEquals("b", cache.get(1, "b", k -> k));
        assertEquals(2, cache.size());
    }

    @Test
    void emptyResolutionIsCachedToo() {
        GenerationCache<String> cache = new GenerationCache<>();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            cache.get(7, "missing", key -> {
                calls.incrementAndGet();
                return "";
            });
        }
        assertEquals(1, calls.get(), "解析为空也应缓存，避免每帧重试");
    }
}
