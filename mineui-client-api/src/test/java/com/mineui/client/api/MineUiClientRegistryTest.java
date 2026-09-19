package com.mineui.client.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    void generationSumsProviders() {
        assertEquals(0L, MineUiClientRegistry.generation());
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
        assertEquals(7L, MineUiClientRegistry.generation());
    }
}
