package com.mineui.ui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.ui.spec.Insets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * 列表容器（歌词 / 播放队列等）：{@code items} 绑定状态里的数组，按 {@code itemTemplate} 逐条实例化。
 * <p>
 * 条目模板里 {@code {item}} / {@code {item.xxx}} 读取当前条目，{@code {state.xxx}} 仍读全局状态；
 * {@code {itemIndex}} / {@code {itemHighlight}} 提供序号与高亮标记。
 * 支持滚轮滚动 + 裁剪、高亮行文字着色、自动滚动居中。
 */
public final class ListViewNode extends ContainerNode {

    /** 单列表最多实例化条目数（防状态数组过大导致节点爆炸）。 */
    public static final int MAX_ITEMS = 512;
    private static final float WHEEL_STEP = 24f;
    private static final String ITEM_PREFIX = "item";

    private final String itemsPath;
    private final Supplier<UiNode> itemFactory;
    private final String highlightPath;
    private final Integer highlightColor;
    private final boolean autoScroll;
    private final float itemGap;

    private final List<UiNode> items = new ArrayList<>();
    private final List<JsonElement> itemData = new ArrayList<>();
    private String itemsSignature = "";
    private int highlightIndex = -1;
    private int centeredIndex = Integer.MIN_VALUE;
    private float scrollOffset;
    private float contentHeight;

    public ListViewNode(NodeStyle style, String itemsPath, Supplier<UiNode> itemFactory,
                        String highlightPath, Integer highlightColor, boolean autoScroll, float itemGap) {
        super(style);
        this.itemsPath = itemsPath;
        this.itemFactory = itemFactory;
        this.highlightPath = highlightPath;
        this.highlightColor = highlightColor;
        this.autoScroll = autoScroll;
        this.itemGap = Math.max(0f, itemGap);
    }

    public Integer highlightColor() {
        return highlightColor;
    }

    public int highlightIndex() {
        return highlightIndex;
    }

    public UiNode highlightedItem() {
        return highlightIndex >= 0 && highlightIndex < items.size() ? items.get(highlightIndex) : null;
    }

    public float scrollOffset() {
        return scrollOffset;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public boolean scrollable() {
        return contentHeight > height + 0.5f;
    }

    /** 程序化滚动（会被钳制到合法范围）。 */
    public void scrollTo(float offset) {
        scrollOffset = clamp(offset);
        layout(x, y);
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            contentHeight = 0;
            return;
        }
        List<JsonElement> list = resolveItems(context.state());
        String signature = list.toString();
        if (!signature.equals(itemsSignature)) {
            rebuild(list);
            itemsSignature = signature;
        }
        highlightIndex = resolveHighlight(context.state());

        Insets pad = style().padding();
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        float contentW = contentWidth(resolvedW, context.availableWidth(), pad);
        float viewportH = contentHeight(resolvedH, context.availableHeight(), pad);

        float used = 0;
        float maxCross = 0;
        boolean first = true;
        for (int i = 0; i < items.size(); i++) {
            UiNode item = items.get(i);
            // 条目上下文同时用于度量与渲染（渲染器读取 node.stateContext()）
            StateAccess itemContext = itemState(context.state(), i);
            item.setStateContext(itemContext);
            item.measure(context.withAvailable(contentW, viewportH).withState(itemContext));
            if (!item.visibleNow()) {
                continue;
            }
            if (!first) {
                used += itemGap;
            }
            used += item.height();
            maxCross = Math.max(maxCross, item.width());
            first = false;
        }
        contentHeight = pad.vertical() + used;
        width = resolvedW >= 0 ? resolvedW : pad.horizontal() + maxCross;
        height = resolvedH >= 0 ? resolvedH : contentHeight;
        scrollOffset = clamp(scrollOffset);
    }

    @Override
    public void layout(float x, float y) {
        super.layout(x, y);
        Insets pad = style().padding();
        // 高亮变化时自动居中（只在变化时生效，避免覆盖用户手动滚动）
        if (autoScroll && highlightIndex >= 0 && highlightIndex != centeredIndex) {
            float center = highlightCenter(pad);
            if (center >= 0) {
                scrollOffset = clamp(center - height / 2f);
                centeredIndex = highlightIndex;
            }
        }
        float cursor = y + pad.top() - scrollOffset;
        boolean first = true;
        for (UiNode item : items) {
            if (!item.visibleNow()) {
                continue;
            }
            if (!first) {
                cursor += itemGap;
            }
            item.layout(x + pad.left(), cursor);
            cursor += item.height();
            first = false;
        }
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (!visibleNow() || !contains(mx, my)) {
            return false;
        }
        for (UiNode child : childrenByZDesc()) {
            if (child.scroll(mx, my, amount)) {
                return true;
            }
        }
        if (!scrollable()) {
            return false;
        }
        float before = scrollOffset;
        scrollOffset = clamp(scrollOffset - (float) amount * WHEEL_STEP);
        if (scrollOffset == before) {
            return false;
        }
        layout(x, y);
        return true;
    }

    // ---------- 内部 ----------

    private void rebuild(List<JsonElement> list) {
        clearChildren();
        items.clear();
        itemData.clear();
        centeredIndex = Integer.MIN_VALUE;
        int count = Math.min(list.size(), MAX_ITEMS);
        for (int i = 0; i < count; i++) {
            UiNode node;
            try {
                node = itemFactory.get();
            } catch (RuntimeException e) {
                node = null;
            }
            if (node == null) {
                continue;
            }
            itemData.add(list.get(i));
            items.add(node);
            addChild(node);
        }
        scrollOffset = 0f;
    }

    private List<JsonElement> resolveItems(StateAccess state) {
        JsonElement element = state.getElement(itemsPath);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<JsonElement> result = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            result.add(item);
        }
        return result;
    }

    private int resolveHighlight(StateAccess state) {
        if (highlightPath == null) {
            return -1;
        }
        String raw = state.get(highlightPath, "-1");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private StateAccess itemState(StateAccess parent, int index) {
        return new ItemAccess(parent, itemData.get(index), index, value -> highlightIndex == value);
    }

    private float highlightCenter(Insets pad) {
        UiNode target = highlightedItem();
        if (target == null || !target.visibleNow()) {
            return -1;
        }
        float offset = pad.top();
        boolean first = true;
        for (UiNode item : items) {
            if (!item.visibleNow()) {
                continue;
            }
            if (!first) {
                offset += itemGap;
            }
            if (item == target) {
                return offset + item.height() / 2f;
            }
            offset += item.height();
            first = false;
        }
        return -1;
    }

    private float clamp(float value) {
        float max = Math.max(0f, contentHeight - height);
        return Math.max(0f, Math.min(value, max));
    }

    /** 条目上下文：{@code item} / {@code item.a.b} / {@code itemIndex} / {@code itemHighlight}，其余委托父状态。 */
    private static final class ItemAccess implements StateAccess {

        private final StateAccess parent;
        private final JsonElement item;
        private final int index;
        private final IntPredicate highlight;

        ItemAccess(StateAccess parent, JsonElement item, int index, IntPredicate highlight) {
            this.parent = parent;
            this.item = item;
            this.index = index;
            this.highlight = highlight;
        }

        @Override
        public String get(String path, String defaultValue) {
            switch (path) {
                case ITEM_PREFIX -> {
                    return primitiveOrJson(item, defaultValue);
                }
                case "itemIndex" -> {
                    return Integer.toString(index);
                }
                case "itemHighlight" -> {
                    return Boolean.toString(highlight.test(index));
                }
                default -> {
                    if (path.startsWith(ITEM_PREFIX + ".")) {
                        JsonElement value = elementAt(item, path.substring(ITEM_PREFIX.length() + 1));
                        return value == null ? defaultValue : primitiveOrJson(value, defaultValue);
                    }
                    return parent.get(path, defaultValue);
                }
            }
        }

        @Override
        public JsonElement getElement(String path) {
            if (path.equals(ITEM_PREFIX)) {
                return item;
            }
            if (path.startsWith(ITEM_PREFIX + ".")) {
                return elementAt(item, path.substring(ITEM_PREFIX.length() + 1));
            }
            return parent.getElement(path);
        }

        private static JsonElement elementAt(JsonElement root, String dotted) {
            JsonElement current = root;
            for (String part : dotted.split("\\.")) {
                if (current instanceof JsonObject object && object.has(part)) {
                    current = object.get(part);
                } else {
                    return null;
                }
            }
            return current;
        }

        private static String primitiveOrJson(JsonElement element, String fallback) {
            if (element == null || element.isJsonNull()) {
                return fallback;
            }
            return element.isJsonPrimitive() ? element.getAsString() : element.toString();
        }
    }
}
