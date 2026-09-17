package com.mineui.ui.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiColorsTest {

    @Test
    void lerpEndpoints() {
        int from = 0xFF102030;
        int to = 0x80A0B0C0;
        assertEquals(from, UiColors.lerp(from, to, 0f));
        assertEquals(to, UiColors.lerp(from, to, 1f));
    }

    @Test
    void lerpMidpointEachChannel() {
        int from = 0xFF000000;
        int to = 0xFF808080;
        assertEquals(0xFF404040, UiColors.lerp(from, to, 0.5f));
    }

    @Test
    void lerpClamps() {
        assertEquals(0xFFFFFFFF, UiColors.lerp(0xFF000000, 0xFFFFFFFF, 5f));
        assertEquals(0xFF000000, UiColors.lerp(0xFF000000, 0xFFFFFFFF, -1f));
    }

    @Test
    void withOpacityScalesAlpha() {
        assertEquals(0x80FFFFFF, UiColors.withOpacity(0xFFFFFFFF, 0.5f));
        assertEquals(0x00FFFFFF, UiColors.withOpacity(0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, UiColors.withOpacity(0xFFFFFFFF, 1f));
    }

    @Test
    void withOpacityKeepsRgb() {
        assertEquals(0x8012AB34, UiColors.withOpacity(0xFF12AB34, 0.5f));
    }
}
