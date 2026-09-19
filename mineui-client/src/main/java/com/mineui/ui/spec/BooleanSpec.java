package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.StateAccess;

/** 布尔属性：字面量，或 {@code "{state.xxx}"} 状态绑定。 */
public record BooleanSpec(boolean literal, String path) {

    public static final BooleanSpec TRUE = new BooleanSpec(true, null);
    public static final BooleanSpec FALSE = new BooleanSpec(false, null);

    public static BooleanSpec of(boolean value) {
        return value ? TRUE : new BooleanSpec(true, null);
    }

    /** 解析：true/false、"{state.x}"、或直接写状态路径 "state.x"。 */
    public static BooleanSpec parse(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return TRUE;
        }
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean() ? TRUE : new BooleanSpec(false, null);
        }
        String text = element.getAsString().trim();
        if (text.isEmpty() || text.equalsIgnoreCase("true")) {
            return TRUE;
        }
        if (text.equalsIgnoreCase("false")) {
            return new BooleanSpec(false, null);
        }
        String path = text;
        if (path.startsWith("{state.") && path.endsWith("}")) {
            path = path.substring("{state.".length(), path.length() - 1);
        } else if (path.startsWith("{local.") && path.endsWith("}")) {
            return new BooleanSpec(false, path.substring(1, path.length() - 1));
        } else if (path.startsWith("state.")) {
            path = path.substring("state.".length());
        } else if (path.startsWith("local.")) {
            return new BooleanSpec(false, path);
        }
        return new BooleanSpec(false, path);
    }

    public boolean test(StateAccess state) {
        if (path == null) {
            return literal;
        }
        String value = state.get(path, "false");
        return value.equalsIgnoreCase("true") || value.equals("1");
    }
}
