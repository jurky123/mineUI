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

    // 渲染变换（由动画控制器/悬停进度驱动，渲染器应用；不影响布局）
    private float animOffsetX;
    private float animOffsetY;
    private float animScale = 1f;
    private float animOpacity = 1f;
    private float animRotation;
    private float hoverProgress;
    private boolean visibleNow = true;
    private boolean hoverEntered;

    protected UiNode(NodeStyle style) {
        this.style = style;
    }

    public NodeStyle style() {
        return style;
    }

    public String id() {
        return style.id();
    }

    /** 点击动作 id（空串表示不可点击）。 */
    public String action() {
        return style.action() == null ? "" : style.action();
    }

    /** 悬浮进入动作 id（空串表示无）。 */
    public String hoverAction() {
        return style.hoverAction() == null ? "" : style.hoverAction();
    }

    public boolean clickable() {
        return !action().isEmpty();
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

    // ---------- 渲染变换 ----------

    public float animOffsetX() {
        return animOffsetX;
    }

    public void setAnimOffsetX(float value) {
        this.animOffsetX = value;
    }

    public float animOffsetY() {
        return animOffsetY;
    }

    public void setAnimOffsetY(float value) {
        this.animOffsetY = value;
    }

    public float animScale() {
        return animScale;
    }

    public void setAnimScale(float value) {
        this.animScale = value;
    }

    public float animOpacity() {
        return animOpacity;
    }

    public void setAnimOpacity(float value) {
        this.animOpacity = value;
    }

    public float animRotation() {
        return animRotation;
    }

    public void setAnimRotation(float value) {
        this.animRotation = value;
    }

    public float hoverProgress() {
        return hoverProgress;
    }

    public void setHoverProgress(float value) {
        this.hoverProgress = value;
    }

    public boolean hasTransform() {
        return animOffsetX != 0f || animOffsetY != 0f || animScale != 1f
                || animOpacity != 1f || animRotation != 0f;
    }

    /** 按 id 查找（深度优先）。 */
    public UiNode findById(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        if (nodeId.equals(id())) {
            return this;
        }
        for (UiNode child : children) {
            UiNode found = child.findById(nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 收集标记了 pulse 的节点。 */
    public void collectPulses(List<UiNode> out) {
        if (style.pulse()) {
            out.add(this);
        }
        for (UiNode child : children) {
            child.collectPulses(out);
        }
    }

    public void mouseMoved(double mx, double my) {
        boolean nowHovered = visibleNow && contains(mx, my);
        if (nowHovered && !hovered) {
            hoverEntered = true;
        }
        hovered = nowHovered;
        boolean blocked = false;
        for (UiNode child : childrenByZDesc()) {
            if (blocked) {
                child.clearHover();
                continue;
            }
            child.mouseMoved(mx, my);
            if (child.visibleNow && child.style().modal() && child.contains(mx, my)) {
                blocked = true;
            }
        }
    }

    /** 命中的最深层带 tooltip 的节点；模态节点之外的区域返回 null。 */
    public UiNode findTooltip(double mx, double my) {
        if (!visibleNow || !contains(mx, my)) {
            return null;
        }
        for (UiNode child : childrenByZDesc()) {
            if (!child.visibleNow) {
                continue;
            }
            UiNode found = child.findTooltip(mx, my);
            if (found != null) {
                return found;
            }
            if (child.style().modal() && child.contains(mx, my)) {
                return null;
            }
        }
        return style().tooltip() != null || hasTooltip() ? this : null;
    }

    /** 节点是否需要悬停提示（子类可扩展，如物品名提示）。 */
    public boolean hasTooltip() {
        return false;
    }

    /**
     * 返回被点击的交互节点（如 ButtonNode）；没有命中返回 null。
     * 命中测试按子节点 z 降序；可见的模态节点会吞掉落到其矩形内的点击。
     */
    public UiNode mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return null;
        }
        for (UiNode child : childrenByZDesc()) {
            if (!child.visibleNow) {
                continue;
            }
            UiNode hit = child.mouseClicked(mx, my, button);
            if (hit != null) {
                return hit;
            }
            if (child.style().modal() && child.contains(mx, my)) {
                return child;
            }
        }
        return clickable() ? this : null;
    }

    /** 鼠标滚轮：返回 true 表示已消费。 */
    public boolean scroll(double mx, double my, double amount) {
        if (!visibleNow || !contains(mx, my)) {
            return false;
        }
        for (UiNode child : childrenByZDesc()) {
            if (child.scroll(mx, my, amount)) {
                return true;
            }
        }
        return false;
    }

    /** 清除自身与子树的悬停状态。 */
    public void clearHover() {
        hovered = false;
        hoverEntered = false;
        for (UiNode child : children) {
            child.clearHover();
        }
    }

    /**
     * 收集本次遍历中 hover 由 false→true 的节点 hoverAction，并清除进入标记。
     * 由界面在每次 mouseMoved 后调用。
     */
    public void collectHoverActions(List<String> out) {
        if (hoverEntered) {
            hoverEntered = false;
            String action = hoverAction();
            if (visibleNow && !action.isEmpty()) {
                out.add(action);
            }
        }
        for (UiNode child : children) {
            child.collectHoverActions(out);
        }
    }

    // ---------- 可见性 ----------

    /** 按状态求值可见性（在 measure 阶段调用并缓存，供输入/渲染使用）。 */
    public boolean evaluateVisible(StateAccess state) {
        visibleNow = style().visible().test(state);
        return visibleNow;
    }

    public boolean visibleNow() {
        return visibleNow;
    }

    public void setVisibleNow(boolean visible) {
        this.visibleNow = visible;
    }

    protected java.util.List<UiNode> childrenByZDesc() {
        java.util.List<UiNode> sorted = new ArrayList<>(children);
        sorted.sort((a, b) -> Integer.compare(b.style().z(), a.style().z()));
        return sorted;
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
