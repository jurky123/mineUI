package com.mineui.ui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.ui.spec.Insets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * 列表容器（歌词 / 播放队列 / 聊天消息等）：{@code items} 绑定状态里的数组，按
 * {@code itemTemplate} 逐条实例化。
 * <p>
 * 条目模板里 {@code {item}} / {@code {item.xxx}} 读取当前条目，{@code {state.xxx}} 仍读全局状态；
 * {@code {itemIndex}} / {@code {itemHighlight}} 提供序号与高亮标记。
 * 支持滚轮滚动 + 裁剪、高亮行文字着色、自动滚动居中。
 * <p>
 * 状态更新时按条目内容增量复用节点（流式回复只重建变化的那一条）；{@code stickToBottom}
 * 让列表在底部时持续跟随新内容，用户向上滚动后暂停跟随。
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
    private final boolean stickToBottom;

    private final List<UiNode> items = new ArrayList<>();
    private final List<JsonElement> itemData = new ArrayList<>();
    private final List<String> itemSignatures = new ArrayList<>();
    private int highlightIndex = -1;
    private int centeredIndex = Integer.MIN_VALUE;
    private float scrollOffset;
    private float contentHeight;
    /** 是否跟随底部（用户上滚后暂停，滚回底部恢复）。 */
    private boolean following;

    public ListViewNode(NodeStyle style, String itemsPath, Supplier<UiNode> itemFactory,
                        String highlightPath, Integer highlightColor, boolean autoScroll, float itemGap) {
        this(style, itemsPath, itemFactory, highlightPath, highlightColor, autoScroll, itemGap, false);
    }

    public ListViewNode(NodeStyle style, String itemsPath, Supplier<UiNode> itemFactory,
                        String highlightPath, Integer highlightColor, boolean autoScroll, float itemGap,
                        boolean stickToBottom) {
        super(style);
        this.itemsPath = itemsPath;
        this.itemFactory = itemFactory;
        this.highlightPath = highlightPath;
        this.highlightColor = highlightColor;
        this.autoScroll = autoScroll;
        this.itemGap = Math.max(0f, itemGap);
        this.stickToBottom = stickToBottom;
        this.following = stickToBottom;
    }

    public Integer highlightColor() {
        return highlightColor;
    }

    public int highlightIndex() {
        return highlightIndex;
    }

    public boolean stickToBottom() {
        return stickToBottom;
    }

    /** 当前是否跟随底部（用户主动上滚后为 false）。 */
    public boolean following() {
        return following;
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
        if (stickToBottom) {
            following = scrollOffset >= maxScroll() - 0.5f;
        }
        layout(x, y);
    }

    /** 回到底部并恢复跟随。 */
    public void scrollToBottom() {
        following = stickToBottom;
        scrollOffset = maxScroll();
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
        syncItems(list);
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
        scrollOffset = following && stickToBottom ? maxScroll() : clamp(scrollOffset);
    }

    @Override
    public void layout(float x, float y) {
        super.layout(x, y);
        Insets pad = style().padding();
        // 最终视口高度可能在度量后被父容器（grow）覆盖：在这里统一校正跟随/钳制
        scrollOffset = stickToBottom && following ? maxScroll() : clamp(scrollOffset);
        // 高亮变化时自动居中（只在变化时生效，避免覆盖用户手动滚动）
        if (autoScroll && highlightIndex >= 0 && highlightIndex != centeredIndex
                && !(stickToBottom && following)) {
            float center = highlightCenter(pad);
            if (center >= 0) {
                scrollOffset = clamp(center - height / 2f);
                centeredIndex = highlightIndex;
                if (stickToBottom) {
                    following = scrollOffset >= maxScroll() - 0.5f;
                }
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
        if (stickToBottom) {
            following = scrollOffset >= maxScroll() - 0.5f;
        }
        layout(x, y);
        return true;
    }

    // ---------- 内部 ----------

    /**
     * 按条目内容增量同步：内容相同的条目复用原节点（流式更新最后一条时只重建该条），
     * 内容变化或数量变化时只替换受影响的位置。
     */
    private void syncItems(List<JsonElement> list) {
        int count = Math.min(list.size(), MAX_ITEMS);
        boolean changed = count != items.size();
        if (!changed) {
            for (int i = 0; i < count; i++) {
                if (!itemSignatures.get(i).equals(list.get(i).toString())) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            return;
        }
        List<UiNode> nextItems = new ArrayList<>(count);
        List<JsonElement> nextData = new ArrayList<>(count);
        List<String> nextSignatures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            JsonElement data = list.get(i);
            String signature = data.toString();
            UiNode node;
            if (i < items.size() && signature.equals(itemSignatures.get(i))) {
                node = items.get(i);
            } else {
                try {
                    node = itemFactory.get();
                } catch (RuntimeException e) {
                    node = null;
                }
            }
            if (node == null) {
                continue;
            }
            node.setItemIndex(i);
            nextItems.add(node);
            nextData.add(data);
            nextSignatures.add(signature);
        }
        items.clear();
        items.addAll(nextItems);
        itemData.clear();
        itemData.addAll(nextData);
        itemSignatures.clear();
        itemSignatures.addAll(nextSignatures);
        clearChildren();
        for (UiNode node : items) {
            addChild(node);
        }
        if (centeredIndex >= items.size()) {
            centeredIndex = Integer.MIN_VALUE;
        }
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

    private float maxScroll() {
        return Math.max(0f, contentHeight - height);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(value, maxScroll()));
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
