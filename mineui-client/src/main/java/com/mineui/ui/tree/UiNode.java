package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI 节点基类：负责度量与布局（纯逻辑，可单测），渲染由客户端渲染器负责。 */
public abstract class UiNode {

    private final NodeStyle style;
    private final List<UiNode> children = new ArrayList<>();

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected boolean hovered;

    protected UiNode(NodeStyle style) {
        this.style = style;
    }

    public NodeStyle style() {
        return style;
    }

    public String id() {
        return style.id();
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public boolean hovered() {
        return hovered;
    }

    public List<UiNode> children() {
        return Collections.unmodifiableList(children);
    }

    protected void addChildInternal(UiNode child) {
        children.add(child);
    }

    /** 计算自身尺寸（含 padding）。 */
    public abstract void measure(MeasureContext context);

    /** 由父容器调用，确定自身位置。 */
    public void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** stretch 等场景下覆盖自身宽度。 */
    public void overrideWidth(float width) {
        this.width = width;
    }

    public void overrideHeight(float height) {
        this.height = height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public void mouseMoved(double mx, double my) {
        hovered = contains(mx, my);
        for (UiNode child : children) {
            child.mouseMoved(mx, my);
        }
    }

    /**
     * 返回被点击的交互节点（如 ButtonNode）；没有命中返回 null。
     * 命中测试按子节点倒序（上层优先）。
     */
    public UiNode mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode hit = children.get(i).mouseClicked(mx, my, button);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    // ---------- 尺寸解析辅助 ----------

    protected float resolveWidth(MeasureContext context) {
        SizeSpec spec = style.width();
        return spec == null ? -1 : spec.resolve(context.availableWidth(), context.viewportWidth(), context.viewportHeight());
    }

    protected float resolveHeight(MeasureContext context) {
        SizeSpec spec = style.height();
        return spec == null ? -1 : spec.resolve(context.availableHeight(), context.viewportWidth(), context.viewportHeight());
    }

    protected static float contentWidth(float resolvedWidth, float availableWidth, Insets padding) {
        float base = resolvedWidth >= 0 ? resolvedWidth : availableWidth;
        return Math.max(0, base - padding.horizontal());
    }

    protected static float contentHeight(float resolvedHeight, float availableHeight, Insets padding) {
        float base = resolvedHeight >= 0 ? resolvedHeight : availableHeight;
        return Math.max(0, base - padding.vertical());
    }
}
