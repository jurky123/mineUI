package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import com.mineui.ui.spec.BooleanSpec;

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
 * @param visible       可见性（支持状态绑定；不可见时不参与布局/渲染/交互）
 * @param tooltip       悬停提示文本（支持 {state.x} 绑定；null 无）
 * @param modal         模态：显示时阻挡下层节点的交互
 * @param action        点击动作 id（任意节点可点击；空/null 表示不可点击。ButtonNode 使用自身 action）
 * @param hoverAction   鼠标进入该节点时发送的动作 id（null 无；用于悬浮预览等）
 * @param sprite        背景精灵（原版九宫格贴图 id，如 minecraft:widget/button；null 用纯色背景）
 * @param spriteHover   悬停时的背景精灵（null 表示不变）
 * @param spriteFocus   聚焦时的背景精灵（输入框用；null 表示不变）
 * @param hoverScale    悬停时的缩放倍数（1 表示不缩放；任意节点可用）
 * @param cycle         本地时间轮换（背景色/物品；null 表示不轮换）
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
        boolean pulse,
        BooleanSpec visible,
        String tooltip,
        boolean modal,
        String action,
        String hoverAction,
        String sprite,
        String spriteHover,
        String spriteFocus,
        float hoverScale,
        com.mineui.ui.spec.CycleSpec cycle) {

    /** Phase 2 兼容构造器（无视觉扩展）。 */
    public NodeStyle(String id, SizeSpec width, SizeSpec height, Insets padding, Integer background,
                     CrossAlign align, MainAlign justify, float gap) {
        this(id, width, height, padding, background, align, justify, gap,
                0f, null, 0f, null, 0f, 0f, null, 0, false, false, BooleanSpec.TRUE, null, false, null, null,
                null, null, null, 1f, null);
    }

    public static NodeStyle defaults() {
        return new NodeStyle(null, SizeSpec.auto(), SizeSpec.auto(), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f);
    }
}
