package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureBudgetTest {

    private static TextureBudget.Usage usage(String key, long bytes, long lastUsed) {
        return new TextureBudget.Usage(key, bytes, lastUsed);
    }

    @Test
    void underBudgetEvictsNothing() {
        List<String> victims = TextureBudget.selectEvictions(
                List.of(usage("a", 10, 0), usage("b", 10, 0)), 100, 1_000, 2_000);
        assertTrue(victims.isEmpty());
    }

    @Test
    void overBudgetEvictsLeastRecentlyUsedFirst() {
        List<String> victims = TextureBudget.selectEvictions(List.of(
                usage("old", 60, 0),
                usage("mid", 60, 100),
                usage("new", 60, 200)), 100, 10_000, 0);
        // 需要降到 100：淘汰最旧的两个才能 ≤100（每项 60）
        assertEquals(List.of("old", "mid"), victims);
    }

    @Test
    void activeEntriesWithinGraceAreProtected() {
        // 总 120 超预算 100：只有 "old"（超出 grace）可淘汰；淘汰后仍 60 ≤100
        List<String> victims = TextureBudget.selectEvictions(List.of(
                usage("old", 60, 1_000),
                usage("active", 60, 9_900)), 100, 10_000, 2_000);
        assertEquals(List.of("old"), victims);
    }

    @Test
    void neverEvictsActiveEntriesEvenIfStillOverBudget() {
        // 全部在活跃窗口内：宁可暂时超预算，也不释放当前可见图像
        List<String> victims = TextureBudget.selectEvictions(List.of(
                usage("a", 60, 9_900),
                usage("b", 60, 9_950)), 100, 10_000, 2_000);
        assertTrue(victims.isEmpty());
    }

    @Test
    void nonPositiveBudgetDisablesEviction() {
        assertTrue(TextureBudget.selectEvictions(List.of(usage("a", 999, 0)), 0, 10_000, 0).isEmpty());
        assertTrue(TextureBudget.selectEvictions(List.of(usage("a", 999, 0)), -1, 10_000, 0).isEmpty());
    }

    @Test
    void emptyUsagesEvictsNothing() {
        assertTrue(TextureBudget.selectEvictions(List.of(), 100, 10_000, 0).isEmpty());
    }
}
