package com.mineui.ui.util;

/** HUD 锚点定位（纯函数，便于单测）。 */
public final class HudAnchor {

    /** 底部安全区：原版 hotbar 高度 + 边距，避免 HUD 压住热键栏。 */
    public static final float SAFE_BOTTOM = 44f;

    private HudAnchor() {
    }

    /**
     * 计算 HUD 左上角坐标（底部锚点默认避开原版 hotbar）。
     *
     * @param anchor  与 {@link com.mineui.protocol.msg.HudLayout} 一致
     * @param offsetX 距锚点水平偏移（像素）
     * @param offsetY 距锚点垂直偏移（像素）
     * @param width   HUD 宽
     * @param height  HUD 高
     */
    public static float[] resolve(String anchor, float offsetX, float offsetY,
                                  float width, float height, float screenWidth, float screenHeight) {
        return resolve(anchor, offsetX, offsetY, width, height, screenWidth, screenHeight, SAFE_BOTTOM);
    }

    /** 可自定义底部安全区（0 表示贴边）。 */
    public static float[] resolve(String anchor, float offsetX, float offsetY,
                                  float width, float height, float screenWidth, float screenHeight,
                                  float safeBottom) {
        String key = anchor == null ? "top_left" : anchor.toLowerCase();
        return switch (key) {
            case "top_center" -> new float[]{(screenWidth - width) / 2f + offsetX, offsetY};
            case "top_right" -> new float[]{screenWidth - width - offsetX, offsetY};
            case "center_left" -> new float[]{offsetX, (screenHeight - height) / 2f + offsetY};
            case "center" -> new float[]{(screenWidth - width) / 2f + offsetX, (screenHeight - height) / 2f + offsetY};
            case "center_right" -> new float[]{screenWidth - width - offsetX, (screenHeight - height) / 2f + offsetY};
            case "bottom_left" -> new float[]{offsetX, screenHeight - height - offsetY - safeBottom};
            case "bottom_center" -> new float[]{(screenWidth - width) / 2f + offsetX, screenHeight - height - offsetY - safeBottom};
            case "bottom_right" -> new float[]{screenWidth - width - offsetX, screenHeight - height - offsetY - safeBottom};
            default -> new float[]{offsetX, offsetY};
        };
    }
}
