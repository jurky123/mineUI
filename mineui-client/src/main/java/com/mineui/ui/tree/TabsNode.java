package com.mineui.ui.tree;

import com.mineui.ui.spec.DoubleSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签页（原版 tab 精灵一排）：选中下标走数值绑定，点击发送 action，
 * 负载 {@code {"index": <点击的页签下标>}}。
 */
public final class TabsNode extends UiNode {

    private final List<String> items;
    private final DoubleSpec selected;
    private final String action;

    private final List<Float> itemWidths = new ArrayList<>();
    private int selectedIndex;
    private int clickedIndex = -1;
    /**
     * 控件值（与 ListViewNode 写入的条目身份 {@code itemIndex} 分开）：
     * 最近一次命中的页下标；独立列表外同样可用。
     */
    private int tabIndex = -1;

    public TabsNode(NodeStyle style, List<String> items, DoubleSpec selected, String action) {
        super(style);
        this.items = items == null ? List.of() : items;
        this.selected = selected == null ? DoubleSpec.of(0) : selected;
        this.action = action == null ? "" : action;
    }

    @Override
    public String action() {
        return action.isEmpty() ? super.action() : action;
    }

    public List<String> items() {
        return items;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    /** 绘制阶段读取选中下标（本地值变化无需等待重排即可刷新外观）。 */
    public int selected(StateAccess state) {
        return (int) Math.round(selected.resolve(state));
    }

    /** 最近一次命中并已记录的页下标（诊断用）。 */
    public int clickedIndex() {
        return clickedIndex;
    }

    /** 控件值：最近一次命中的页下标（与条目身份分开，见 U6）。 */
    public int tabIndex() {
        return tabIndex;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        selectedIndex = (int) Math.round(selected.resolve(context.state()));
        itemWidths.clear();
        float total = 0;
        for (String raw : items) {
            float itemWidth = context.text().width(Bindings.resolve(raw, context.state())) + 16;
            itemWidths.add(itemWidth);
            total += itemWidth;
        }
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : Math.max(1, total);
        height = resolvedH >= 0 ? resolvedH : 20f;
    }

    /** 第 i 页宽度（渲染与命中测试共用同一度量）。 */
    public float itemWidth(int index) {
        return index >= 0 && index < itemWidths.size() ? itemWidths.get(index) : 0f;
    }

    @Override
    public UiNode mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return null;
        }
        int index = indexAt(mx);
        if (index < 0) {
            return null;
        }
        // 控件值走 tabIndex；条目身份（itemIndex）保持 ListViewNode 的写入，不被覆盖
        tabIndex = index;
        clickedIndex = index;
        return this;
    }

    /** 命中的页下标；未命中返回 -1。 */
    private int indexAt(double mx) {
        float cursor = x;
        for (int i = 0; i < items.size(); i++) {
            float itemWidth = itemWidth(i);
            if (mx >= cursor && mx < cursor + itemWidth) {
                return i;
            }
            cursor += itemWidth;
        }
        return -1;
    }
}
