package com.mineui.ui.util;

import java.util.function.ToIntFunction;

/** 单行文本省略（纯函数，便于单测）。 */
public final class TextFit {

    private TextFit() {
    }

    /**
     * 超出宽度时截断并以省略号结尾。
     *
     * @param text        原始文本
     * @param maxWidth    最大显示宽度（单位与 width 度量一致）
     * @param widthOf     字符串宽度度量（如 font::width）
     */
    public static String ellipsize(String text, float maxWidth, ToIntFunction<String> widthOf) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return text == null ? "" : text;
        }
        if (widthOf.applyAsInt(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        if (widthOf.applyAsInt(ellipsis) > maxWidth) {
            return "";
        }
        // 按码位二分：不会把代理对（emoji 等）从中间截断；零宽字符不影响收敛
        int[] codePoints = text.codePoints().toArray();
        int lo = 0;
        int hi = codePoints.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (widthOf.applyAsInt(new String(codePoints, 0, mid) + ellipsis) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return new String(codePoints, 0, lo) + ellipsis;
    }
}
