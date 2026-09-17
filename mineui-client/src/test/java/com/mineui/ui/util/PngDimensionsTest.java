package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PngDimensionsTest {

    @Test
    void readsBundledIconDimensions() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mineui/textures/gui/icon_star.png")) {
            assertNotNull(in);
            assertArrayEquals(new int[]{16, 16}, PngDimensions.read(in));
        }
    }

    @Test
    void readsOwnLogoDimensions() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mineui/textures/gui/logo.png")) {
            assertNotNull(in);
            assertArrayEquals(new int[]{16, 16}, PngDimensions.read(in));
        }
    }

    @Test
    void rejectsNonPngData() {
        byte[] data = "definitely not a png file".getBytes(StandardCharsets.UTF_8);
        assertNull(PngDimensions.read(new ByteArrayInputStream(data)));
    }

    @Test
    void rejectsTruncatedData() {
        assertNull(PngDimensions.read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        assertNull(PngDimensions.read(null));
    }
}
