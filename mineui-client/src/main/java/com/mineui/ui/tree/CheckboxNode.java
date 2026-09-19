package com.mineui.ui.tree;

import com.mineui.ui.spec.BooleanSpec;

/** 复选框（原版 checkbox 精灵）：选中态走绑定，点击发送 action。 */
public final class CheckboxNode extends UiNode {

    public static final int DEFAULT_SIZE = 16;

    private final BooleanSpec value;
    private final String action;
    private boolean checkedNow;

    public CheckboxNode(NodeStyle style, BooleanSpec value, String action) {
        super(style);
        this.value = value == null ? BooleanSpec.FALSE : value;
        this.action = action == null ? "" : action;
    }

    @Override
    public String action() {
        return action.isEmpty() ? super.action() : action;
    }

    public boolean checkedNow() {
        return checkedNow;
    }

    /** 绘制阶段读取（本地值变化无需等待重排即可刷新外观）。 */
    public boolean checked(StateAccess state) {
        return value.test(state);
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        checkedNow = value.test(context.state());
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : DEFAULT_SIZE;
        height = resolvedH >= 0 ? resolvedH : DEFAULT_SIZE;
    }

    @Override
    public UiNode mouseClicked(double mx, double my, int button) {
        return contains(mx, my) ? this : null;
    }
}
