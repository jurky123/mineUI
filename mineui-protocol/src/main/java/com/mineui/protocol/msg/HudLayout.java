package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * HUD 布局（服务端默认值；客户端本地偏好可覆盖）。
 *
 * @param anchor  锚点：top_left / top_center / top_right / center_left / center / center_right /
 *                bottom_left / bottom_center / bottom_right
 * @param offsetX 距锚点水平偏移（像素）
 * @param offsetY 距锚点垂直偏移（像素）
 * @param scale   缩放（默认 1.0）
 */
public record HudLayout(String anchor, float offsetX, float offsetY, float scale) {

    public static final String DEFAULT_ANCHOR = "top_left";

    public HudLayout {
        anchor = anchor == null || anchor.isBlank() ? DEFAULT_ANCHOR : anchor;
        scale = scale <= 0f ? 1f : scale;
    }

    public static HudLayout defaults() {
        return new HudLayout(DEFAULT_ANCHOR, 4f, 4f, 1f);
    }

    /** 从 JSON 解析（缺省字段用默认值）；null/非对象返回默认。 */
    public static HudLayout parse(JsonObject json) {
        if (json == null) {
            return defaults();
        }
        return new HudLayout(
                json.has("anchor") ? json.get("anchor").getAsString() : DEFAULT_ANCHOR,
                json.has("offsetX") ? json.get("offsetX").getAsFloat() : 4f,
                json.has("offsetY") ? json.get("offsetY").getAsFloat() : 4f,
                json.has("scale") ? json.get("scale").getAsFloat() : 1f);
    }
}
