package com.mineui.ui.tree;

/** 交叉轴对齐。 */
public enum CrossAlign {
    START,
    CENTER,
    END,
    STRETCH;

    public static CrossAlign parse(String value, CrossAlign fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.toLowerCase()) {
            case "start" -> START;
            case "center" -> CENTER;
            case "end" -> END;
            case "stretch" -> STRETCH;
            default -> fallback;
        };
    }
}
