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
    private final String sha256;
    /** 着色（ARGB 乘色；0 表示无 tint，用原色渲染）。 */
    private final int tint;
    /**
     * 纹理过滤：{@code ""} 缺省（远程/烘焙变体为最近邻，资源包贴图跟随 mcmeta），
     * {@code "nearest"} 强制最近邻（像素风放大不糊），{@code "linear"} 强制线性。
     */
    private final String filter;

    public ImageNode(NodeStyle style, String texture, float u, float v,
                     float regionWidth, float regionHeight, float textureWidth, float textureHeight) {
        this(style, texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight, null, 0, "");
    }

    public ImageNode(NodeStyle style, String texture, float u, float v,
                     float regionWidth, float regionHeight, float textureWidth, float textureHeight,
                     String sha256, int tint) {
        this(style, texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight,
                sha256, tint, "");
    }

    public ImageNode(NodeStyle style, String texture, float u, float v,
                     float regionWidth, float regionHeight, float textureWidth, float textureHeight,
                     String sha256, int tint, String filter) {
        super(style);
        this.texture = texture == null ? "" : texture;
        this.u = u;
        this.v = v;
        // 0 表示未指定：region 未指定 = 整张图，textureSize 未指定 = 读取贴图真实尺寸
        this.regionWidth = Math.max(0f, regionWidth);
        this.regionHeight = Math.max(0f, regionHeight);
        this.textureWidth = Math.max(0f, textureWidth);
        this.textureHeight = Math.max(0f, textureHeight);
        this.sha256 = sha256 == null || sha256.isBlank() ? null : sha256;
        this.tint = tint;
        this.filter = normalizeFilter(filter);
    }

    /** 着色 ARGB（0 表示无 tint）。 */
    public int tint() {
        return tint;
    }

    /** 纹理过滤："" 缺省 / "nearest" / "linear"。 */
    public String filter() {
        return filter;
    }

    /** 归一化 filter 值：只接受 nearest/linear，其余视为缺省。 */
    public static String normalizeFilter(String filter) {
        if (filter == null) {
            return "";
        }
        String value = filter.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "nearest", "linear" -> value;
            default -> "";
        };
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

    /** 远程图片的可选 sha256 校验值。 */
    public String sha256() {
        return sha256;
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
