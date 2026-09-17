package com.mineui.ui.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {

    @Test
    void startsAtZero() {
        for (Easing easing : Easing.values()) {
            assertEquals(0f, easing.apply(0f), 0.001f, easing.name());
        }
    }

    @Test
    void endsAtOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(1f, easing.apply(1f), 0.05f, easing.name());
        }
    }

    @Test
    void clampsInput() {
        for (Easing easing : Easing.values()) {
            assertEquals(0f, easing.apply(-5f), 0.001f, easing.name());
            assertEquals(1f, easing.apply(5f), 0.05f, easing.name());
        }
    }

    @Test
    void linearMidpoint() {
        assertEquals(0.5f, Easing.LINEAR.apply(0.5f), 0.0001f);
    }

    @Test
    void backOutOvershoots() {
        boolean overshoot = false;
        for (float t = 0f; t <= 1f; t += 0.02f) {
            if (Easing.BACK_OUT.apply(t) > 1.001f) {
                overshoot = true;
                break;
            }
        }
        assertTrue(overshoot, "BACK_OUT 应在中段过冲");
    }

    @Test
    void parseNames() {
        assertEquals(Easing.EASE_OUT, Easing.parse("ease_out", Easing.LINEAR));
        assertEquals(Easing.EASE_IN_OUT, Easing.parse("EASE-IN-OUT", Easing.LINEAR));
        assertEquals(Easing.SPRING, Easing.parse("spring", Easing.LINEAR));
        assertEquals(Easing.LINEAR, Easing.parse("nope", Easing.LINEAR));
    }
}
