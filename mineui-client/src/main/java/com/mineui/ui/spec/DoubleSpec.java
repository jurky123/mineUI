package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.StateAccess;

/** 数值属性：字面量，或 {@code "{state.xxx}"} 状态绑定（用于滑块当前值等）。 */
public record DoubleSpec(double literal, String path) {

    public static DoubleSpec of(double value) {
        return new DoubleSpec(value, null);
    }

    /** 解析：数字、"{state.x}" 或 "state.x"。 */
    public static DoubleSpec parse(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) {
            return of(fallback);
        }
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return of(primitive.getAsDouble());
        }
        String text = element.getAsString().trim();
        if (text.isEmpty()) {
            return of(fallback);
        }
        String path = text;
        if (path.startsWith("{state.") && path.endsWith("}")) {
            path = path.substring("{state.".length(), path.length() - 1);
        } else if (path.startsWith("state.")) {
            path = path.substring("state.".length());
        } else {
            try {
                return of(Double.parseDouble(text));
            } catch (NumberFormatException e) {
                return of(fallback);
            }
        }
        return new DoubleSpec(fallback, path);
    }

    public double resolve(StateAccess state) {
        if (path == null) {
            return literal;
        }
        try {
            return Double.parseDouble(state.get(path, Double.toString(literal)));
        } catch (NumberFormatException e) {
            return literal;
        }
    }
}
