package com.mineui.ui.tree;

import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.DoubleSpec;

import java.util.Locale;

/**
 * 进度条组件（原版/自定义风格由样式决定）。
 * <p>
 * 支持低频更新的客户端平滑插值：服务端 1Hz 下发 {@code value}，客户端按本地时钟外推；
 * seek/切歌（跳变超过 snapThreshold）时立即对齐。插值只影响显示，不改状态。
 */
public final class ProgressNode extends UiNode {

    public enum Direction {
        LEFT_RIGHT, RIGHT_LEFT, TOP_BOTTOM, BOTTOM_TOP;

        public static Direction parse(String value, Direction fallback) {
            if (value == null) {
                return fallback;
            }
            return switch (value.toLowerCase().replace('-', '_')) {
                case "left_right", "left2right", "horizontal" -> LEFT_RIGHT;
                case "right_left", "right2left" -> RIGHT_LEFT;
                case "top_bottom", "vertical" -> TOP_BOTTOM;
                case "bottom_top" -> BOTTOM_TOP;
                default -> fallback;
            };
        }
    }

    /** 外推上限：超过该时长没有新样本就停止外推，避免失控。 */
    private static final long MAX_EXTRAPOLATION_MILLIS = 5000L;
    private static final double VALUE_EPSILON = 1e-4;

    private final DoubleSpec valueSpec;
    private final double min;
    private final double max;
    private final Direction direction;
    private final int fillColor;
    private final Integer fillGradientTo;
    private final BooleanSpec playing;
    private final boolean interpolate;
    private final double explicitRate;
    private final double snapThreshold;
    private final String text;
    private final String timeFormat;
    private final int textColor;

    // 插值状态（客户端运行时）
    private boolean hasSample;
    private double lastServerValue;
    private long lastSampleMillis;
    private double derivedRate;

    public ProgressNode(NodeStyle style, DoubleSpec valueSpec, double min, double max, Direction direction,
                        int fillColor, Integer fillGradientTo, BooleanSpec playing, boolean interpolate,
                        double explicitRate, double snapThreshold, String text, String timeFormat, int textColor) {
        super(style);
        this.valueSpec = valueSpec == null ? DoubleSpec.of(min) : valueSpec;
        this.min = min;
        this.max = Math.max(min + 1e-6, max);
        this.direction = direction == null ? Direction.LEFT_RIGHT : direction;
        this.fillColor = fillColor;
        this.fillGradientTo = fillGradientTo;
        this.playing = playing == null ? BooleanSpec.TRUE : playing;
        this.interpolate = interpolate;
        this.explicitRate = Math.max(0, explicitRate);
        this.snapThreshold = snapThreshold <= 0 ? 3.0 : snapThreshold;
        this.text = text == null ? "" : text;
        this.timeFormat = timeFormat == null || timeFormat.isBlank() ? null : timeFormat;
        this.textColor = textColor;
    }

    public Direction direction() {
        return direction;
    }

    public int fillColor() {
        return fillColor;
    }

    public Integer fillGradientTo() {
        return fillGradientTo;
    }

    public int textColor() {
        return textColor;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** 服务端权威值（钳制到范围）。 */
    public double serverValue(StateAccess state) {
        return clamp(valueSpec.resolve(state));
    }

    /**
     * 显示值：插值开启且播放中时按本地时钟外推；否则等于服务端值。
     *
     * @param nowMillis 当前时间（毫秒，可注入便于测试）
     */
    public double displayValue(StateAccess state, long nowMillis) {
        double value = serverValue(state);
        if (!interpolate) {
            return value;
        }
        sample(value, nowMillis);
        if (!playing.test(state)) {
            return value;
        }
        double rate = explicitRate > 0 ? explicitRate : derivedRate;
        if (rate == 0) {
            return value;
        }
        long elapsed = nowMillis - lastSampleMillis;
        if (elapsed <= 0 || elapsed > MAX_EXTRAPOLATION_MILLIS) {
            return clamp(lastServerValue);
        }
        return clamp(lastServerValue + rate * (elapsed / 1000.0));
    }

    public double ratioOf(double value) {
        return (clamp(value) - min) / (max - min);
    }

    /** 标签：{value}/{max} 可用 {@code timeFormat} 格式化为时间；另支持 {percent} 与 {state.x}。 */
    public String label(StateAccess state, double display) {
        if (text.isEmpty()) {
            return "";
        }
        String valueText = timeFormat != null ? formatTime(display) : formatNumber(display);
        String maxText = timeFormat != null ? formatTime(max) : formatNumber(max);
        String percent = Integer.toString((int) Math.round(ratioOf(display) * 100));
        String replaced = text.replace("{value}", valueText).replace("{max}", maxText).replace("{percent}", percent);
        return Bindings.resolve(replaced, state);
    }

    /** 秒 → "m:ss" 或 "h:mm:ss"。 */
    public static String formatTime(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void sample(double value, long nowMillis) {
        if (!hasSample) {
            hasSample = true;
            lastServerValue = value;
            lastSampleMillis = nowMillis;
            return;
        }
        if (Math.abs(value - lastServerValue) < VALUE_EPSILON) {
            return;
        }
        long elapsed = nowMillis - lastSampleMillis;
        double delta = value - lastServerValue;
        if (Math.abs(delta) > snapThreshold) {
            // seek/切歌：立即对齐，保留既有速率（若有）
        } else if (explicitRate <= 0 && elapsed > 0) {
            derivedRate = delta / (elapsed / 1000.0);
        }
        lastServerValue = value;
        lastSampleMillis = nowMillis;
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
        width = resolvedW >= 0 ? resolvedW : 160f;
        height = resolvedH >= 0 ? resolvedH : 10f;
    }

    private double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }
}
