package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HudAnchorTest {

    private static final float W = 100f;
    private static final float H = 50f;
    private static final float SW = 800f;
    private static final float SH = 600f;
    private static final float OFF = 4f;

    private void assertPosition(String anchor, float expectedX, float expectedY) {
        float[] position = HudAnchor.resolve(anchor, OFF, OFF, W, H, SW, SH);
        assertEquals(expectedX, position[0], 0.001f, anchor + " x");
        assertEquals(expectedY, position[1], 0.001f, anchor + " y");
    }

    @Test
    void cornersAndCenters() {
        assertPosition("top_left", 4f, 4f);
        assertPosition("top_center", (SW - W) / 2f + OFF, 4f);
        assertPosition("top_right", SW - W - OFF, 4f);
        assertPosition("center_left", 4f, (SH - H) / 2f + OFF);
        assertPosition("center", (SW - W) / 2f + OFF, (SH - H) / 2f + OFF);
        assertPosition("center_right", SW - W - OFF, (SH - H) / 2f + OFF);
        assertPosition("bottom_left", 4f, SH - H - OFF - HudAnchor.SAFE_BOTTOM);
        assertPosition("bottom_center", (SW - W) / 2f + OFF, SH - H - OFF - HudAnchor.SAFE_BOTTOM);
        assertPosition("bottom_right", SW - W - OFF, SH - H - OFF - HudAnchor.SAFE_BOTTOM);
    }

    @Test
    void unknownAnchorFallsBackToTopLeft() {
        assertPosition("diagonal", 4f, 4f);
        assertPosition(null, 4f, 4f);
    }

    @Test
    void anchorIsCaseInsensitive() {
        assertPosition("BOTTOM_RIGHT", SW - W - OFF, SH - H - OFF - HudAnchor.SAFE_BOTTOM);
    }

    @Test
    void zeroOffsetSnapsToEdge() {
        assertArrayEquals(new float[]{0f, 0f}, HudAnchor.resolve("top_left", 0f, 0f, W, H, SW, SH), 0.001f);
        assertArrayEquals(new float[]{SW - W, SH - H - HudAnchor.SAFE_BOTTOM},
                HudAnchor.resolve("bottom_right", 0f, 0f, W, H, SW, SH), 0.001f);
    }

    @Test
    void zeroSafeBottomRestoresEdge() {
        assertArrayEquals(new float[]{SW - W, SH - H},
                HudAnchor.resolve("bottom_right", 0f, 0f, W, H, SW, SH, 0f), 0.001f);
    }
}
