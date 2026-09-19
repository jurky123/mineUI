package com.mineui.ui.spec;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.StateAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoubleSpecTest {

    @Test
    void literalValue() {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive(42), 0);
        assertEquals(42, spec.resolve(StateAccess.EMPTY), 0.001);
    }

    @Test
    void numericString() {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive("3.5"), 0);
        assertEquals(3.5, spec.resolve(StateAccess.EMPTY), 0.001);
    }

    @Test
    void bindingResolvesFromState() {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive("{state.volume}"), 1);
        assertEquals(70, spec.resolve((path, fallback) -> "70"), 0.001);
    }

    @Test
    void bindingFallsBackWhenMissingOrInvalid() {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive("{state.volume}"), 5);
        assertEquals(5, spec.resolve((path, fallback) -> fallback), 0.001);
        assertEquals(5, spec.resolve((path, fallback) -> "abc"), 0.001);
    }

    @Test
    void missingElementUsesFallback() {
        DoubleSpec spec = DoubleSpec.parse(null, 9);
        assertEquals(9, spec.resolve(StateAccess.EMPTY), 0.001);
    }

    @Test
    void localBindingKeepsPrefixAndResolves() {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive("{local.mineaudio.position}"), 0);
        assertEquals("local.mineaudio.position", spec.path());
        assertEquals(31.5, spec.resolve((path, fallback) -> "31.5"), 0.001);

        DoubleSpec bare = DoubleSpec.parse(new JsonPrimitive("local.mineaudio.volume"), 0);
        assertEquals("local.mineaudio.volume", bare.path());
    }
}
