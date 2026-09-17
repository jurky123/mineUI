package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 内边距（四边）。支持数字（四边相同）或 {top,right,bottom,left}。 */
public record Insets(float top, float right, float bottom, float left) {

    public static final Insets ZERO = new Insets(0, 0, 0, 0);

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }

    public static Insets all(float value) {
        return new Insets(value, value, value, value);
    }

    public static Insets parse(JsonElement element) throws UiSpecException {
        if (element == null || element.isJsonNull()) {
            return ZERO;
        }
        if (element.isJsonPrimitive()) {
            float value = element.getAsFloat();
            return all(value);
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return new Insets(
                    opt(obj, "top"),
                    opt(obj, "right"),
                    opt(obj, "bottom"),
                    opt(obj, "left"));
        }
        throw new UiSpecException("无法解析 padding/margin: " + element);
    }

    private static float opt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsFloat() : 0f;
    }
}
