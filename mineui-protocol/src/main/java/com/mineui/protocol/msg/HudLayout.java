package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * HUD 布局（服务端默认值；客户端本地偏好可覆盖位置/缩放/开关）。
 *
 * @param anchor  锚点：top_left / top_center / top_right / center_left / center / center_right /
 *                bottom_left / bottom_center / bottom_right
 * @param offsetX 距锚点水平偏移（像素）
 * @param offsetY 距锚点垂直偏移（像素）
 * @param scale   缩放（默认 1.0）
 * @param visible 服务端默认是否可见（客户端本地开关优先）
 * @param z       同屏多个 HUD 的渲染顺序（小的先画）
 */
public record HudLayout(String anchor, float offsetX, float offsetY, float scale, boolean visible, int z) {

    public static final String DEFAULT_ANCHOR = "top_left";

    /** 兼容构造：默认可见、z=0。 */
    public HudLayout(String anchor, float offsetX, float offsetY, float scale) {
        this(anchor, offsetX, offsetY, scale, true, 0);
    }

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
        boolean visible = !json.has("visible") || json.get("visible").isJsonNull()
                || json.get("visible").getAsBoolean();
        return new HudLayout(
                json.has("anchor") ? json.get("anchor").getAsString() : DEFAULT_ANCHOR,
                json.has("offsetX") ? json.get("offsetX").getAsFloat() : 4f,
                json.has("offsetY") ? json.get("offsetY").getAsFloat() : 4f,
                json.has("scale") ? json.get("scale").getAsFloat() : 1f,
                visible,
                json.has("z") && !json.get("z").isJsonNull() ? json.get("z").getAsInt() : 0);
    }
}
