package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextWrapTest {

    /** 假字体：每字符 6px（省略号也占一个字符）。 */
    private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

    @Test
    void cjkWrapsPerCharacterWithoutSpaces() {
        // 12px = 2 个字符一行
        assertEquals(List.of("中文", "测试", "文本"),
                TextWrap.wrap("中文测试文本", 12, WIDTH, 0));
    }

    @Test
    void englishWordsAreNotSplitMidWord() {
        // 42px = 7 个字符：如果硬断会得到 "alpha b" / "eta"；应在空格处换行
        assertEquals(List.of("alpha", "beta"),
                TextWrap.wrap("alpha beta", 42, WIDTH, 0));
    }

    @Test
    void explicitNewlinesArePreserved() {
        assertEquals(List.of("a", "b", "", "c"),
                TextWrap.wrap("a\nb\n\nc", 1000, WIDTH, 0));
    }

    @Test
    void trailingNewlineDoesNotAddBlankLine() {
        assertEquals(List.of("a", "b"),
                TextWrap.wrap("a\nb\n", 1000, WIDTH, 0));
    }

    @Test
    void maxLinesTruncatesLastLineWithEllipsis() {
        List<String> lines = TextWrap.wrap("abcdef", 12, WIDTH, 2);

        assertEquals(List.of("ab", "c…"), lines);
        assertTrue(lines.get(lines.size() - 1).endsWith(TextWrap.ELLIPSIS));
    }

    @Test
    void nonPositiveWidthOnlyHonorsExplicitNewlines() {
        assertEquals(List.of("a very long line with words", "second"),
                TextWrap.wrap("a very long line with words\nsecond", 0, WIDTH, 0));
        assertEquals(List.of("line one", "line two"),
                TextWrap.wrap("line one\nline two", -10, WIDTH, 0));
    }

    @Test
    void overlongWordIsHardBroken() {
        // 无空格可断时按字符硬断，避免死循环/超宽行
        assertEquals(List.of("abc", "def", "ghi", "j"),
                TextWrap.wrap("abcdefghij", 18, WIDTH, 0));
    }
}
