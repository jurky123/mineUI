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
        this.regionWidth = regionWidth <= 0 ? 16 : regionWidth;
        this.regionHeight = regionHeight <= 0 ? 16 : regionHeight;
        this.textureWidth = textureWidth <= 0 ? 256 : textureWidth;
        this.textureHeight = textureHeight <= 0 ? 256 : textureHeight;
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
        width = resolvedW >= 0 ? resolvedW : regionWidth;
        height = resolvedH >= 0 ? resolvedH : regionHeight;
    }
}
