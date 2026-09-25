package com.mineui.client.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineUiClientRegistryTest {

    @AfterEach
    void cleanup() {
        MineUiClientRegistry.clear();
    }

    @Test
    void stateLookupByNamespace() {
        MineUiClientBridge.get().register("mineaudio", key -> "position".equals(key) ? 12.5 : null, null);

        assertEquals(12.5, MineUiClientRegistry.getState("mineaudio", "position"));
        assertNull(MineUiClientRegistry.getState("mineaudio", "missing"));
        assertNull(MineUiClientRegistry.getState("other", "position"));
    }

    @Test
    void unregisterStopsLookup() throws Exception {
        AutoCloseable handle = MineUiClientBridge.get().register("ns", key -> "v", null);
        assertEquals("v", MineUiClientRegistry.getState("ns", "k"));
        handle.close();
        assertNull(MineUiClientRegistry.getState("ns", "k"));
    }

    @Test
    void dispatchOnlyToSameNamespace() {
        MineUiClientBridge.get().register("a", null, (action, payload) -> "seek".equals(action));
        MineUiClientBridge.get().register("b", null, (action, payload) -> true);

        assertTrue(MineUiClientRegistry.dispatchAction("a", "seek", Map.of("value", 1.0)));
        assertFalse(MineUiClientRegistry.dispatchAction("a", "other", Map.of()));
        assertFalse(MineUiClientRegistry.dispatchAction("unknown", "seek", Map.of()));
    }

    @Test
    void providerExceptionIsIsolated() {
        MineUiClientBridge.get().register("bad", key -> {
            throw new IllegalStateException("boom");
        }, (action, payload) -> {
            throw new IllegalStateException("boom");
        });

        assertNull(MineUiClientRegistry.getState("bad", "k"));
        assertFalse(MineUiClientRegistry.dispatchAction("bad", "k", Map.of()));
    }

    @Test
    void capabilitiesReflectRegistrations() {
        assertFalse(MineUiClientRegistry.hasStateProviders());
        assertFalse(MineUiClientRegistry.hasActionHandlers());

        MineUiClientBridge.get().register("ns", key -> null, (action, payload) -> false);
        assertTrue(MineUiClientRegistry.hasStateProviders());
        assertTrue(MineUiClientRegistry.hasActionHandlers());

        MineUiClientBridge.get().declareCapability("mineaudio_local");
        assertTrue(MineUiClientRegistry.declaredCapabilities().contains("mineaudio_local"));
    }

    @Test
    void generationIncludesRegistryVersionAndProviders() {
        long before = MineUiClientRegistry.generation();
        MineUiClientBridge.get().register("a", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public long generation() {
                return 3L;
            }
        }, null);
        MineUiClientBridge.get().register("b", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public long generation() {
                return 4L;
            }
        }, null);
        // 注册表版本（+2）加上两个 provider 的 generation（3+4）
        assertEquals(before + 2 + 7, MineUiClientRegistry.generation());
    }

    @Test
    void imageLookupOnlyForOverridingProviders() {
        MineUiClientBridge.get().register("plain", key -> null, null);
        assertFalse(MineUiClientRegistry.hasImageProviders());
        assertNull(MineUiClientRegistry.getImage("plain", "k"));

        MineUiClientBridge.get().register("img", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public byte[] image(String key) {
                return "cover".equals(key) ? new byte[]{1, 2, 3} : null;
            }
        }, null);
        assertTrue(MineUiClientRegistry.hasImageProviders());
        assertArrayEquals(new byte[]{1, 2, 3}, MineUiClientRegistry.getImage("img", "cover"));
        assertNull(MineUiClientRegistry.getImage("img", "missing"));
        assertNull(MineUiClientRegistry.getImage("other", "cover"));
    }

    @Test
    void imageProviderExceptionIsIsolated() {
        MineUiClientBridge.get().register("bad", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public byte[] image(String key) {
                throw new IllegalStateException("boom");
            }
        }, null);
        assertNull(MineUiClientRegistry.getImage("bad", "k"));
    }

    @Test
    void namespaceGenerationTracksItsProviderOnly() {
        MineUiClientBridge.get().register("a", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public long generation() {
                return 5L;
            }
        }, null);
        MineUiClientBridge.get().register("b", new ClientStateProvider() {
            @Override
            public Object get(String key) {
                return null;
            }

            @Override
            public long generation() {
                return 7L;
            }
        }, null);
        long version = MineUiClientRegistry.generation() - 12;
        assertEquals(version + 5, MineUiClientRegistry.generation("a"));
        assertEquals(version + 7, MineUiClientRegistry.generation("b"));
        assertEquals(version, MineUiClientRegistry.generation("unknown"));
    }

    @Test
    void registerAndUnregisterChangeGeneration() throws Exception {
        long before = MineUiClientRegistry.generation();
        AutoCloseable handle = MineUiClientBridge.get().register("ns", key -> null, null);
        long afterRegister = MineUiClientRegistry.generation();
        assertTrue(afterRegister > before, "注册应改变结构代数");

        handle.close();
        long afterClose = MineUiClientRegistry.generation();
        assertTrue(afterClose > afterRegister, "注销应改变结构代数");
    }
}
