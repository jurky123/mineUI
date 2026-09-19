package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFitTest {

    private static final java.util.function.ToIntFunction<String> WIDTH = String::length;

    @Test
    void shortTextUnchanged() {
        assertEquals("hello", TextFit.ellipsize("hello", 10, WIDTH));
    }

    @Test
    void longTextTruncatedWithEllipsis() {
        // 宽度 6：5 字符 + "…"（宽 1）
        assertEquals("hello…", TextFit.ellipsize("hello world", 6, WIDTH));
    }

    @Test
    void tinyWidthLeavesOnlyEllipsisOrEmpty() {
        assertEquals("…", TextFit.ellipsize("hello", 1, WIDTH));
        assertEquals("", TextFit.ellipsize("hello", 0.5f, WIDTH));
    }

    @Test
    void exactFitUnchanged() {
        assertEquals("hello", TextFit.ellipsize("hello", 5, WIDTH));
    }
}
