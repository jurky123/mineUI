package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** 尺寸定义：像素 / auto / 百分比 / vw / vh。 */
public final class SizeSpec {

    public enum Unit { PX, AUTO, PERCENT, VW, VH }

    private final Unit unit;
    private final float value;

    private SizeSpec(Unit unit, float value) {
        this.unit = unit;
        this.value = value;
    }

    public static SizeSpec auto() {
        return new SizeSpec(Unit.AUTO, 0);
    }

    public static SizeSpec px(float value) {
        return new SizeSpec(Unit.PX, value);
    }

    public Unit unit() {
        return unit;
    }

    public float value() {
        return value;
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    /**
     * 解析为像素；auto 返回 -1。
     *
     * @param available 父容器可用空间（用于 %）
     * @param viewportW 屏幕宽（用于 vw）
     * @param viewportH 屏幕高（用于 vh）
     */
    public float resolve(float available, float viewportW, float viewportH) {
        return switch (unit) {
            case PX -> value;
            case PERCENT -> available * value / 100f;
            case VW -> viewportW * value / 100f;
            case VH -> viewportH * value / 100f;
            case AUTO -> -1;
        };
    }

    public static SizeSpec parse(JsonElement element) throws UiSpecException {
        if (element == null || element.isJsonNull()) {
            return auto();
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            String text = element.getAsString().trim().toLowerCase();
            if (text.isEmpty() || text.equals("auto")) {
                return auto();
            }
            try {
                if (text.endsWith("px")) {
                    return px(Float.parseFloat(text.substring(0, text.length() - 2).trim()));
                }
                if (text.endsWith("vw")) {
                    return new SizeSpec(Unit.VW, Float.parseFloat(text.substring(0, text.length() - 2).trim()));
                }
                if (text.endsWith("vh")) {
                    return new SizeSpec(Unit.VH, Float.parseFloat(text.substring(0, text.length() - 2).trim()));
                }
                if (text.endsWith("%")) {
                    return new SizeSpec(Unit.PERCENT, Float.parseFloat(text.substring(0, text.length() - 1).trim()));
                }
                return px(Float.parseFloat(text));
            } catch (NumberFormatException e) {
                throw new UiSpecException("无法解析尺寸: " + text);
            }
        }
        return px(primitive.getAsFloat());
    }
}
