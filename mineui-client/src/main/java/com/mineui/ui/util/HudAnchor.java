package com.mineui.ui.util;

/** HUD 锚点定位（纯函数，便于单测）。 */
public final class HudAnchor {

    private HudAnchor() {
    }

    /**
     * 计算 HUD 左上角坐标。
     *
     * @param anchor  与 {@link com.mineui.protocol.msg.HudLayout} 一致
     * @param offsetX 距锚点水平偏移（像素）
     * @param offsetY 距锚点垂直偏移（像素）
     * @param width   HUD 宽
     * @param height  HUD 高
     */
    public static float[] resolve(String anchor, float offsetX, float offsetY,
                                  float width, float height, float screenWidth, float screenHeight) {
        String key = anchor == null ? "top_left" : anchor.toLowerCase();
        return switch (key) {
            case "top_center" -> new float[]{(screenWidth - width) / 2f + offsetX, offsetY};
            case "top_right" -> new float[]{screenWidth - width - offsetX, offsetY};
            case "center_left" -> new float[]{offsetX, (screenHeight - height) / 2f + offsetY};
            case "center" -> new float[]{(screenWidth - width) / 2f + offsetX, (screenHeight - height) / 2f + offsetY};
            case "center_right" -> new float[]{screenWidth - width - offsetX, (screenHeight - height) / 2f + offsetY};
            case "bottom_left" -> new float[]{offsetX, screenHeight - height - offsetY};
            case "bottom_center" -> new float[]{(screenWidth - width) / 2f + offsetX, screenHeight - height - offsetY};
            case "bottom_right" -> new float[]{screenWidth - width - offsetX, screenHeight - height - offsetY};
            default -> new float[]{offsetX, offsetY};
        };
    }
}
