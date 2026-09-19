package com.mineui.ui.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BindingsTest {

    /** 按路径前缀返回固定值的假状态。 */
    private static final StateAccess STATE = (path, fallback) -> switch (path) {
        case "title" -> "hello";
        case "local.mineaudio.position" -> "42";
        case "key.mineaudio.open" -> "F7";
        case "key.mineaudio:open_ui" -> "F7";
        default -> fallback;
    };

    @Test
    void resolvesStateAndKeyAndLocal() {
        assertEquals("hello", Bindings.resolve("{state.title}", STATE));
        assertEquals("F7", Bindings.resolve("{key.mineaudio.open}", STATE));
        assertEquals("42", Bindings.resolve("{local.mineaudio.position}", STATE));
    }

    @Test
    void keybindHintAllowsNamespacedActionId() {
        assertEquals("F7", Bindings.resolve("{key.mineaudio:open_ui}", STATE));
        assertEquals("按 F7 打开", Bindings.resolve("按 {key.mineaudio:open_ui} 打开", STATE));
    }

    @Test
    void mixedTemplateResolvesAllNamespaces() {
        assertEquals("hello-42-F7", Bindings.resolve("{state.title}-{local.mineaudio.position}-{key.mineaudio.open}", STATE));
    }

    @Test
    void unknownLocalFallsBackEmpty() {
        assertEquals("", Bindings.resolve("{local.mineaudio.missing}", STATE));
    }

    @Test
    void statePathOnlyAcceptsStateBinding() {
        assertEquals("lyrics", Bindings.statePath("{state.lyrics}"));
        assertNull(Bindings.statePath("{local.mineaudio.lyrics}"));
        assertNull(Bindings.statePath("plain"));
    }
}
