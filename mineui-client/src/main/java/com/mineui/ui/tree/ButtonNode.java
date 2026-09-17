package com.mineui.ui.tree;

/** 按钮节点：点击后由客户端发送 ACTION（action id）。 */
public final class ButtonNode extends UiNode {

    private final String template;
    private final String action;
    private final int background;
    private final int hoverBackground;
    private final int textColor;

    public ButtonNode(NodeStyle style, String template, String action,
                      int background, int hoverBackground, int textColor) {
        super(style);
        this.template = template == null ? "" : template;
        this.action = action == null ? "" : action;
        this.background = background;
        this.hoverBackground = hoverBackground;
        this.textColor = textColor;
    }

    public String template() {
        return template;
    }

    @Override
    public String action() {
        return action.isEmpty() ? super.action() : action;
    }

    public int background() {
        return background;
    }

    public int hoverBackground() {
        return hoverBackground;
    }

    public int textColor() {
        return textColor;
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
        width = resolvedW >= 0 ? resolvedW : context.text().width(text) + 16;
        height = resolvedH >= 0 ? resolvedH : context.text().lineHeight() + 8;
    }

    @Override
    public UiNode mouseClicked(double mx, double my, int button) {
        return contains(mx, my) ? this : null;
    }
}
