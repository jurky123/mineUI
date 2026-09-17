package com.mineui.ui.paint;

/** 颜色工具（ARGB）。 */
public final class UiColors {

    private UiColors() {
    }

    /** 线性插值两色（含 alpha）。 */
    public static int lerp(int from, int to, float t) {
        float x = t < 0 ? 0 : (t > 1 ? 1 : t);
        int a = lerpChannel(from >>> 24, to >>> 24, x);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, x);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, x);
        int b = lerpChannel(from & 0xFF, to & 0xFF, x);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 乘以透明度（0..1）。 */
    public static int withOpacity(int color, float opacity) {
        float x = opacity < 0 ? 0 : (opacity > 1 ? 1 : opacity);
        int a = Math.round(((color >>> 24) & 0xFF) * x);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
