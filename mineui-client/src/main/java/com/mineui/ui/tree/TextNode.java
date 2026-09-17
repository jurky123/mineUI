package com.mineui.ui.tree;

/** 文本节点。{@code text} 支持 {@code {state.xxx}} 绑定。 */
public final class TextNode extends UiNode {

    private final String template;
    private final int color;
    private final float scale;

    public TextNode(NodeStyle style, String template, int color, float scale) {
        super(style);
        this.template = template == null ? "" : template;
        this.color = color;
        this.scale = scale <= 0 ? 1f : scale;
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

    @Override
    public void measure(MeasureContext context) {
        String text = Bindings.resolve(template, context.state());
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : context.text().width(text) * scale;
        height = resolvedH >= 0 ? resolvedH : context.text().lineHeight() * scale;
    }
}
