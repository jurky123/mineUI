package com.mineui.ui.anim;

/** 缓动函数（输入 0..1，返回 0..1，允许轻微过冲如 BACK_OUT/SPRING）。 */
public enum Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BACK_OUT,
    SPRING;

    public float apply(float t) {
        float x = t < 0 ? 0 : (t > 1 ? 1 : t);
        return switch (this) {
            case LINEAR -> x;
            case EASE_IN -> x * x;
            case EASE_OUT -> 1f - (1f - x) * (1f - x);
            case EASE_IN_OUT -> x < 0.5f ? 2f * x * x : 1f - 2f * (1f - x) * (1f - x);
            case BACK_OUT -> {
                float c1 = 1.70158f;
                float c3 = c1 + 1f;
                float p = x - 1f;
                yield 1f + c3 * p * p * p + c1 * p * p;
            }
            case SPRING -> (float) (1f - Math.exp(-7f * x) * Math.cos(11f * x));
        };
    }

    public static Easing parse(String value, Easing fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.toLowerCase()) {
            case "linear" -> LINEAR;
            case "ease_in", "ease-in" -> EASE_IN;
            case "ease_out", "ease-out" -> EASE_OUT;
            case "ease_in_out", "ease-in-out" -> EASE_IN_OUT;
            case "back_out", "back-out" -> BACK_OUT;
            case "spring" -> SPRING;
            default -> fallback;
        };
    }
}
