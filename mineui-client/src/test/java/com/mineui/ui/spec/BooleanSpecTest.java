package com.mineui.ui.spec;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.StateAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanSpecTest {

    private static StateAccess state(String value) {
        return (path, fallback) -> value == null ? fallback : value;
    }

    @Test
    void literalValues() {
        assertTrue(BooleanSpec.parse(new JsonPrimitive(true)).test(state(null)));
        assertFalse(BooleanSpec.parse(new JsonPrimitive(false)).test(state(null)));
        assertTrue(BooleanSpec.parse(null).test(state(null)));
        assertTrue(BooleanSpec.parse(new JsonPrimitive("true")).test(state(null)));
        assertFalse(BooleanSpec.parse(new JsonPrimitive("false")).test(state(null)));
    }

    @Test
    void bindingTruthyValues() {
        BooleanSpec spec = BooleanSpec.parse(new JsonPrimitive("{state.show}"));
        assertTrue(spec.test(state("true")));
        assertTrue(spec.test(state("TRUE")));
        assertTrue(spec.test(state("1")));
        assertFalse(spec.test(state("false")));
        assertFalse(spec.test(state("0")));
        assertFalse(spec.test(state(null)));
    }

    @Test
    void bareStatePathAlsoWorks() {
        BooleanSpec spec = BooleanSpec.parse(new JsonPrimitive("state.flag"));
        assertTrue(spec.test(state("true")));
        assertFalse(spec.test(state("false")));
    }
}
