package com.mineui.ui.tree;

/**
 * 度量上下文。
 *
 * @param availableWidth  父容器提供的可用宽（已扣父 padding）
 * @param availableHeight 父容器提供的可用高（已扣父 padding）
 * @param viewportWidth   屏幕逻辑宽（vw 基准）
 * @param viewportHeight  屏幕逻辑高（vh 基准）
 */
public record MeasureContext(
        float availableWidth,
        float availableHeight,
        float viewportWidth,
        float viewportHeight,
        TextMeasurer text,
        StateAccess state) {

    /** 基于父上下文、替换可用空间。 */
    public MeasureContext withAvailable(float width, float height) {
        return new MeasureContext(width, height, viewportWidth, viewportHeight, text, state);
    }

    /** 基于父上下文、替换状态（列表条目上下文等）。 */
    public MeasureContext withState(StateAccess newState) {
        return new MeasureContext(availableWidth, availableHeight, viewportWidth, viewportHeight, text, newState);
    }
}
