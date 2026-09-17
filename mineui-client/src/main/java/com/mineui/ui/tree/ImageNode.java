package com.mineui.ui.tree;

/** 贴图节点（区域 uv 以纹理像素为单位）。 */
public final class ImageNode extends UiNode {

    private final String texture;
    private final float u;
    private final float v;
    private final float regionWidth;
    private final float regionHeight;
    private final float textureWidth;
    private final float textureHeight;

    public ImageNode(NodeStyle style, String texture, float u, float v,
                     float regionWidth, float regionHeight, float textureWidth, float textureHeight) {
        super(style);
        this.texture = texture == null ? "" : texture;
        this.u = u;
        this.v = v;
        // 0 表示未指定：region 未指定 = 整张图，textureSize 未指定 = 读取贴图真实尺寸
        this.regionWidth = Math.max(0f, regionWidth);
        this.regionHeight = Math.max(0f, regionHeight);
        this.textureWidth = Math.max(0f, textureWidth);
        this.textureHeight = Math.max(0f, textureHeight);
    }

    public String texture() {
        return texture;
    }

    public float u() {
        return u;
    }

    public float v() {
        return v;
    }

    public float regionWidth() {
        return regionWidth;
    }

    public float regionHeight() {
        return regionHeight;
    }

    public float textureWidth() {
        return textureWidth;
    }

    public float textureHeight() {
        return textureHeight;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float fallbackW = regionWidth > 0 ? regionWidth : (textureWidth > 0 ? textureWidth : 16f);
        float fallbackH = regionHeight > 0 ? regionHeight : (textureHeight > 0 ? textureHeight : 16f);
        width = resolvedW >= 0 ? resolvedW : fallbackW;
        height = resolvedH >= 0 ? resolvedH : fallbackH;
    }
}
