package com.mineui.ui.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundedRectTest {

    @Test
    void insetAtEdgeEqualsRadius() {
        assertEquals(6f, RoundedRect.insetFor(0f, 6f), 0.001f);
    }

    @Test
    void insetZeroBeyondRadius() {
        assertEquals(0f, RoundedRect.insetFor(6f, 6f), 0.001f);
        assertEquals(0f, RoundedRect.insetFor(9f, 6f), 0.001f);
    }

    @Test
    void insetDecreasesMonotonically() {
        float previous = RoundedRect.insetFor(0f, 8f);
        for (float d = 0.5f; d <= 8f; d += 0.5f) {
            float current = RoundedRect.insetFor(d, 8f);
            assertEquals(true, current <= previous + 0.0001f);
            previous = current;
        }
    }

    @Test
    void zeroRadiusMeansNoInset() {
        float[][] rows = RoundedRect.rowInsets(20f, 0f);
        assertEquals(20, rows.length);
        for (float[] row : rows) {
            assertEquals(0f, row[0], 0.001f);
            assertEquals(0f, row[1], 0.001f);
        }
    }

    @Test
    void cornersInsetAndMiddleIsFull() {
        float[][] rows = RoundedRect.rowInsets(20f, 4f);
        assertEquals(20, rows.length);
        assertEquals(RoundedRect.insetFor(0.5f, 4f), rows[0][0], 0.001f);
        assertEquals(0f, rows[10][0], 0.001f);
        assertEquals(rows[0][0], rows[19][0], 0.001f);
    }

    @Test
    void oneRowRectStillWorks() {
        float[][] rows = RoundedRect.rowInsets(1f, 3f);
        assertEquals(1, rows.length);
    }
}
