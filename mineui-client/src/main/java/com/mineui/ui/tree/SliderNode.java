package com.mineui.ui.tree;

import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.DoubleSpec;

import java.util.Locale;

/**
 * 滑块控件（原版风格贴图由渲染层负责）。
 * 拖动过程中只更新本地显示，松开时提交 {@code action}，负载 {@code {"value": <数值>}}。
 */
public final class SliderNode extends UiNode {

    private static final float HANDLE_WIDTH = 8f;

    private final DoubleSpec valueSpec;
    private final double min;
    private final double max;
    private final String action;
    private final String message;
    private final int textColor;
    private final double step;
    private final BooleanSpec enabled;

    private Double dragValue;
    private boolean enabledNow = true;

    public SliderNode(NodeStyle style, DoubleSpec valueSpec, double min, double max,
                      String action, String message, int textColor) {
        this(style, valueSpec, min, max, action, message, textColor, 0, BooleanSpec.TRUE);
    }

    public SliderNode(NodeStyle style, DoubleSpec valueSpec, double min, double max,
                      String action, String message, int textColor, double step, BooleanSpec enabled) {
        super(style);
        this.valueSpec = valueSpec == null ? DoubleSpec.of(min) : valueSpec;
        this.min = min;
        this.max = Math.max(min + 0.0001, max);
        this.action = action == null ? "" : action;
        this.message = message == null ? "" : message;
        this.textColor = textColor;
        this.step = Math.max(0, step);
        this.enabled = enabled == null ? BooleanSpec.TRUE : enabled;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String action() {
        return action;
    }

    public int textColor() {
        return textColor;
    }

    /** 步进（0 = 连续）。 */
    public double step() {
        return step;
    }

    /** 是否可用（禁用时不响应拖动）。 */
    public boolean enabledNow() {
        return enabledNow;
    }

    public boolean dragging() {
        return dragValue != null;
    }

    /** 当前值：拖动中优先显示拖动值。 */
    public double value(StateAccess state) {
        if (dragValue != null) {
            return dragValue;
        }
        return clamp(valueSpec.resolve(state));
    }

    /** 0..1 的位置比例。 */
    public double ratio(StateAccess state) {
        return (value(state) - min) / (max - min);
    }

    /** 由位置比例换算数值（钳制 + 可选步进吸附）。 */
    public double valueFromX(double mouseX) {
        float usable = Math.max(1f, width - HANDLE_WIDTH);
        double ratio = (mouseX - x - HANDLE_WIDTH / 2.0) / usable;
        return snap(clamp(min + ratio * (max - min)));
    }

    /** 标签文本：支持 {value} 与 {state.x} 绑定。 */
    public String label(StateAccess state) {
        if (message.isEmpty()) {
            return "";
        }
        String valueText = Math.abs(value(state) - Math.rint(value(state))) < 0.001
                ? Long.toString(Math.round(value(state)))
                : String.format(Locale.ROOT, "%.2f", value(state));
        return Bindings.resolve(message.replace("{value}", valueText), state);
    }

    public boolean beginDrag(double mouseX, double mouseY) {
        if (!enabledNow || !contains(mouseX, mouseY)) {
            return false;
        }
        dragValue = valueFromX(mouseX);
        return true;
    }

    public boolean dragTo(double mouseX) {
        if (dragValue == null) {
            return false;
        }
        dragValue = valueFromX(mouseX);
        return true;
    }

    /** 结束拖动并返回最终值（无拖动返回 null）。 */
    public Double finishDrag() {
        Double value = dragValue;
        dragValue = null;
        return value;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        enabledNow = enabled.test(context.state());
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : 160f;
        height = resolvedH >= 0 ? resolvedH : 20f;
    }

    @Override
    public UiNode mouseClicked(double mx, double my, int button) {
        return enabledNow && contains(mx, my) ? this : null;
    }

    private double snap(double value) {
        if (step <= 0) {
            return value;
        }
        double steps = Math.round((value - min) / step);
        return clamp(min + steps * step);
    }

    private double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
}
