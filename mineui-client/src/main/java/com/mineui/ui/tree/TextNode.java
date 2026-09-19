package com.mineui.ui.tree;

/**
 * 文本节点。{@code text} 支持 {@code {state.xxx}} 绑定；{@code ellipsis: true} 时
 * 超出节点宽度自动截断加省略号（需配合显式宽度，如 {@code "width": "100%"}）；
 * {@code outline} 为 1px 描边色（ARGB，0 表示无）。
 */
public final class TextNode extends UiNode {

    private final String template;
    private final int color;
    private final float scale;
    private final boolean ellipsis;
    private final int outlineColor;

    public TextNode(NodeStyle style, String template, int color, float scale) {
        this(style, template, color, scale, false, 0);
    }

    public TextNode(NodeStyle style, String template, int color, float scale, boolean ellipsis, int outlineColor) {
        super(style);
        this.template = template == null ? "" : template;
        this.color = color;
        this.scale = scale <= 0 ? 1f : scale;
        this.ellipsis = ellipsis;
        this.outlineColor = outlineColor;
    }

    public String template() {
        return template;
    }

    public int color() {
        return color;
    }

    public float scale() {
        return scale;
    }

    /** 是否超出宽度时截断加省略号。 */
    public boolean ellipsis() {
        return ellipsis;
    }

    /** 1px 描边色（0 表示无）。 */
    public int outlineColor() {
        return outlineColor;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        String text = Bindings.resolve(template, context.state());
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : context.text().width(text) * scale;
        height = resolvedH >= 0 ? resolvedH : context.text().lineHeight() * scale;
    }
}
