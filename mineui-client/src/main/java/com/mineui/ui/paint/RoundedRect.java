package com.mineui.ui.paint;

/** 圆角矩形扫描线计算（纯函数，便于单测）。 */
public final class RoundedRect {

    private RoundedRect() {
    }

    /**
     * 每行（从 y 向下的整数行）左右内缩量。
     *
     * @return {@code float[rows][2]}：{leftInset, rightInset}
     */
    public static float[][] rowInsets(float height, float radius) {
        int rows = Math.max(0, (int) Math.ceil(height));
        float r = Math.max(0f, radius);
        float[][] insets = new float[rows][2];
        for (int i = 0; i < rows; i++) {
            float dy = i + 0.5f;
            float inset = Math.max(insetFor(dy, r), insetFor(height - dy, r));
            insets[i][0] = inset;
            insets[i][1] = inset;
        }
        return insets;
    }

    /** 距边缘 d 处的水平内缩；d >= radius 时为 0。 */
    public static float insetFor(float d, float radius) {
        if (radius <= 0f || d >= radius) {
            return 0f;
        }
        float dx = radius - Math.max(0f, d);
        return radius - (float) Math.sqrt(Math.max(0f, radius * radius - dx * dx));
    }
}
