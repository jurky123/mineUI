package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;

/** 节点通用样式/布局属性（解析自 JSON）。 */
public record NodeStyle(
        String id,
        SizeSpec width,
        SizeSpec height,
        Insets padding,
        Integer background,
        CrossAlign align,
        MainAlign justify,
        float gap) {

    public static NodeStyle defaults() {
        return new NodeStyle(null, SizeSpec.auto(), SizeSpec.auto(), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f);
    }
}
