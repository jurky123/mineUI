package com.mineui.ui.util;

import com.google.gson.JsonParser;
import com.mineui.protocol.msg.HudLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudLayoutTest {

    @Test
    void defaults() {
        HudLayout layout = HudLayout.defaults();
        assertEquals("top_left", layout.anchor());
        assertEquals(4f, layout.offsetX(), 0.001f);
        assertEquals(4f, layout.offsetY(), 0.001f);
        assertEquals(1f, layout.scale(), 0.001f);
    }

    @Test
    void parsesPartialJson() {
        HudLayout layout = HudLayout.parse(JsonParser.parseString("""
                { "anchor": "bottom_right", "scale": 1.5 }
                """).getAsJsonObject());
        assertEquals("bottom_right", layout.anchor());
        assertEquals(4f, layout.offsetX(), 0.001f, "缺省 offset 用默认值");
        assertEquals(1.5f, layout.scale(), 0.001f);
    }

    @Test
    void normalizesBlankAnchorAndInvalidScale() {
        HudLayout layout = new HudLayout("  ", 0f, 0f, 0f);
        assertEquals("top_left", layout.anchor());
        assertEquals(1f, layout.scale(), 0.001f);
    }

    @Test
    void nullJsonReturnsDefaults() {
        assertEquals(HudLayout.defaults(), HudLayout.parse(null));
    }
}
