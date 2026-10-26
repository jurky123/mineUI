package com.mineui.ui.tree;

/** 按钮节点：点击后由客户端发送 ACTION（action id）。 */
public final class ButtonNode extends UiNode {

    private final String template;
    private final String action;
    private final int background;
    private final int hoverBackground;
    private final int textColor;
    /** 点击时是否取输入框当前内容一起提交（发送按钮用）。 */
    private final boolean submitInput;
    /** 指定提交目标输入框 id（null 表示聚焦中的输入框/最近祖先里的第一个）。 */
    private final String submitTarget;
    /** 按下态（鼠标按住期间）：渲染时反转 bevel/加深并下沉 1px。 */
    private boolean pressed;

    public ButtonNode(NodeStyle style, String template, String action,
                      int background, int hoverBackground, int textColor) {
        this(style, template, action, background, hoverBackground, textColor, false, null);
    }

    public ButtonNode(NodeStyle style, String template, String action,
                      int background, int hoverBackground, int textColor, boolean submitInput) {
        this(style, template, action, background, hoverBackground, textColor, submitInput, null);
    }

    public ButtonNode(NodeStyle style, String template, String action,
                      int background, int hoverBackground, int textColor,
                      boolean submitInput, String submitTarget) {
        super(style);
        this.template = template == null ? "" : template;
        this.action = action == null ? "" : action;
        this.background = background;
        this.hoverBackground = hoverBackground;
        this.textColor = textColor;
        this.submitInput = submitInput;
        this.submitTarget = submitTarget == null || submitTarget.isBlank() ? null : submitTarget;
    }

    public String template() {
        return template;
    }

    @Override
    public String action() {
        return action.isEmpty() ? super.action() : action;
    }

    public boolean pressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
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

    /** 点击时取输入框当前内容作为负载一起提交（{@code submit: true}）。 */
    public boolean submitInput() {
        return submitInput;
    }

    /** 提交目标输入框 id（{@code submit: "<input id>"}）；null 表示自动选择。 */
    public String submitTarget() {
        return submitTarget;
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
