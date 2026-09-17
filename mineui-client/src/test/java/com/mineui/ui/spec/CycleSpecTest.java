package com.mineui.ui.spec;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CycleSpecTest {

    private static CycleSpec parse(String json) throws UiSpecException {
        return CycleSpec.parse(JsonParser.parseString(json));
    }

    @Test
    void parsesColors() throws Exception {
        CycleSpec spec = parse("""
                { "interval": 1.0, "colors": ["#FF0000", "#00FF00"] }
                """);
        assertNotNull(spec);
        assertEquals(2, spec.colors().size());
        assertEquals(0.0f, spec.intervalSeconds() - 1.0f, 0.001f);
    }

    @Test
    void parsesItems() throws Exception {
        CycleSpec spec = parse("""
                { "items": ["minecraft:red_wool", "minecraft:blue_wool"] }
                """);
        assertNotNull(spec);
        assertEquals(2, spec.items().size());
        assertEquals(1.0f, spec.intervalSeconds(), 0.001f);
    }

    @Test
    void clampsTooSmallInterval() throws Exception {
        CycleSpec spec = parse("""
                { "interval": 0.001, "colors": ["#FFFFFF"] }
                """);
        assertEquals(0.05f, spec.intervalSeconds(), 0.001f);
    }

    @Test
    void returnsNullWhenNoValues() throws Exception {
        assertNull(parse("{ \"interval\": 1.0 }"));
        assertNull(parse("null"));
        assertNull(CycleSpec.parse(null));
    }

    @Test
    void colorAtCyclesOverTime() throws Exception {
        CycleSpec spec = parse("""
                { "interval": 1.0, "colors": ["#111111", "#222222"] }
                """);
        assertEquals(0xFF111111, spec.colorAt(0, 0));
        assertEquals(0xFF111111, spec.colorAt(999, 0));
        assertEquals(0xFF222222, spec.colorAt(1000, 0));
        assertEquals(0xFF111111, spec.colorAt(2000, 0));
    }

    @Test
    void itemAtCyclesOverTime() throws Exception {
        CycleSpec spec = parse("""
                { "interval": 0.5, "items": ["a", "b", "c"] }
                """);
        assertEquals("a", spec.itemAt(0, "x"));
        assertEquals("b", spec.itemAt(500, "x"));
        assertEquals("c", spec.itemAt(1000, "x"));
        assertEquals("a", spec.itemAt(1500, "x"));
    }

    @Test
    void fallbackWhenEmpty() throws Exception {
        CycleSpec colors = parse("""
                { "colors": ["#FFFFFF"] }
                """);
        assertEquals("fallback", colors.itemAt(0, "fallback"));
        CycleSpec items = parse("""
                { "items": ["a"] }
                """);
        assertEquals(0xFF123456, items.colorAt(0, 0xFF123456));
    }
}
