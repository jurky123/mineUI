package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mineui.ui.tree.StateAccess;

/**
 * 布尔属性：字面量，或 {@code "{state.xxx}"} / {@code "{item.xxx}"} 状态绑定。
 *
 * @param negate 是否取反（用于 {@code disabled} 这类反向语义字段）
 */
public record BooleanSpec(boolean literal, String path, boolean negate) {

    public static final BooleanSpec TRUE = new BooleanSpec(true, null, false);
    public static final BooleanSpec FALSE = new BooleanSpec(false, null, false);

    /** 兼容旧构造：不取反。 */
    public BooleanSpec(boolean literal, String path) {
        this(literal, path, false);
    }

    public static BooleanSpec of(boolean value) {
        return value ? TRUE : new BooleanSpec(true, null, false);
    }

    /** 返回取反后的规格（{@code disabled} → 可用态）。 */
    public BooleanSpec negated() {
        return new BooleanSpec(literal, path, !negate);
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
        // 列表条目上下文：{item.xxx} / {itemIndex} / {itemHighlight}
        if (text.endsWith("}") && (text.equals("{item}") || text.startsWith("{item.")
                || text.equals("{itemIndex}") || text.equals("{itemHighlight}"))) {
            return new BooleanSpec(false, text.substring(1, text.length() - 1));
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
        boolean value;
        if (path == null) {
            value = literal;
        } else {
            String raw = state.get(path, "false");
            value = raw.equalsIgnoreCase("true") || raw.equals("1");
        }
        return negate != value;
    }
}
