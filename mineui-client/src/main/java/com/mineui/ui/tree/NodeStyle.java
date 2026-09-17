package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;

/**
 * 节点通用样式/布局属性（解析自 JSON）。
 *
 * @param radius        圆角半径
 * @param borderColor   描边色（null 无描边）
 * @param borderWidth   描边宽度
 * @param shadowColor   阴影色（null 无阴影）
 * @param shadowSize    阴影外扩像素
 * @param shadowOffsetY 阴影纵向偏移
 * @param gradientTo    纵向渐变结束色（null 纯色背景）
 * @param z             同级渲染层级（大在上）
 * @param clip          是否裁剪子节点到自身矩形
 * @param pulse         状态 revision 变化时是否播放一次脉冲动画
 */
public record NodeStyle(
        String id,
        SizeSpec width,
        SizeSpec height,
        Insets padding,
        Integer background,
        CrossAlign align,
        MainAlign justify,
        float gap,
        float radius,
        Integer borderColor,
        float borderWidth,
        Integer shadowColor,
        float shadowSize,
        float shadowOffsetY,
        Integer gradientTo,
        int z,
        boolean clip,
        boolean pulse) {

    /** Phase 2 兼容构造器（无视觉扩展）。 */
    public NodeStyle(String id, SizeSpec width, SizeSpec height, Insets padding, Integer background,
                     CrossAlign align, MainAlign justify, float gap) {
        this(id, width, height, padding, background, align, justify, gap,
                0f, null, 0f, null, 0f, 0f, null, 0, false, false);
    }

    public static NodeStyle defaults() {
        return new NodeStyle(null, SizeSpec.auto(), SizeSpec.auto(), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f);
    }
}
