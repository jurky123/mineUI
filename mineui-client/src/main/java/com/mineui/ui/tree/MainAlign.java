package com.mineui.ui.tree;

/** 主轴对齐。 */
public enum MainAlign {
    START,
    CENTER,
    END,
    SPACE_BETWEEN;

    public static MainAlign parse(String value, MainAlign fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.toLowerCase()) {
            case "start" -> START;
            case "center" -> CENTER;
            case "end" -> END;
            case "space-between" -> SPACE_BETWEEN;
            default -> fallback;
        };
    }
}
