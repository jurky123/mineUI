package com.mineui.ui.tree;

import com.mineui.ui.util.TextWrap;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 文本节点。{@code text} 支持 {@code {state.xxx}} 绑定；{@code ellipsis: true} 时
 * 超出节点宽度自动截断加省略号（需配合显式宽度，如 {@code "width": "100%"}）；
 * {@code outline} 为 1px 描边色（ARGB，0 表示无）。
 * <p>
 * {@code wrap: true} 时按节点宽度自动换行，高度随行数增长；{@code maxLines} 超出后
 * 截断并以省略号收尾；{@code lineHeight} 为行距（像素，缺省用字体行高）。
 */
public final class TextNode extends UiNode {

    private final String template;
    private final int color;
    private final float scale;
    private final boolean ellipsis;
    private final int outlineColor;
    private final boolean wrap;
    private final int maxLines;
    private final float lineHeight;

    // 换行结果缓存：同一文本/宽度在一帧内被度量与渲染各调用一次，避免重复逐字测量
    private String cachedText;
    private float cachedWidth = Float.NaN;
    private List<String> cachedLines;

    public TextNode(NodeStyle style, String template, int color, float scale) {
        this(style, template, color, scale, false, 0);
    }

    public TextNode(NodeStyle style, String template, int color, float scale, boolean ellipsis, int outlineColor) {
        this(style, template, color, scale, ellipsis, outlineColor, false, 0, 0f);
    }

    public TextNode(NodeStyle style, String template, int color, float scale, boolean ellipsis, int outlineColor,
                    boolean wrap, int maxLines, float lineHeight) {
        super(style);
        this.template = template == null ? "" : template;
        this.color = color;
        this.scale = scale <= 0 ? 1f : scale;
        this.ellipsis = ellipsis;
        this.outlineColor = outlineColor;
        this.wrap = wrap;
        this.maxLines = Math.max(0, maxLines);
        this.lineHeight = Math.max(0f, lineHeight);
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

    /** 是否按宽度自动换行（多行文本）。 */
    public boolean wrap() {
        return wrap;
    }

    /** 最多显示行数（0 表示不限）。 */
    public int maxLines() {
        return maxLines;
    }

    /** 行距（像素；0 表示用字体行高）。 */
    public float lineHeight() {
        return lineHeight;
    }

    /** 单行实际行距（渲染与度量共用）。 */
    public float effectiveLineHeight(int fontLineHeight) {
        return lineHeight > 0f ? lineHeight : fontLineHeight;
    }

    /** 按宽度换行后的行文本（度量/渲染共用同一套规则）。 */
    public List<String> lines(String text, float maxWidth, TextMeasurer measurer) {
        return lines(text, maxWidth, measurer::width);
    }

    /**
     * 按宽度换行后的行文本；对相同文本/宽度做一次缓存（流式更新只失效变化的节点，
     * 渲染阶段不再逐帧重排全部历史消息）。
     */
    public List<String> lines(String text, float maxWidth, ToIntFunction<String> measure) {
        if (cachedLines != null && text.equals(cachedText)
                && Math.abs(maxWidth - cachedWidth) < 0.01f) {
            return cachedLines;
        }
        cachedLines = List.copyOf(TextWrap.wrap(text, maxWidth, measure, maxLines));
        cachedText = text;
        cachedWidth = maxWidth;
        return cachedLines;
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
        if (!wrap) {
            width = resolvedW >= 0 ? resolvedW : context.text().width(text) * scale;
            height = resolvedH >= 0 ? resolvedH : context.text().lineHeight() * scale;
            return;
        }
        float maxWidth = resolvedW >= 0 ? resolvedW : Math.max(0f, context.availableWidth());
        List<String> lines = lines(text, maxWidth / scale, context.text());
        width = resolvedW >= 0 ? resolvedW : maxWidth;
        height = resolvedH >= 0 ? resolvedH : lines.size() * effectiveLineHeight(context.text().lineHeight()) * scale;
    }
}
