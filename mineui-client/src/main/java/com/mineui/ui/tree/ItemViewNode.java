package com.mineui.ui.tree;

import java.util.List;

/**
 * 物品展示节点：按物品 id / 数量 / CustomModelData 渲染（卡面、商品图标等）。
 */
public final class ItemViewNode extends UiNode {

    private final String item;
    private final int count;
    private final int model;
    private final List<String> modelStrings;
    private final float scale;
    private final boolean itemTooltip;
    private final boolean fake;

    public ItemViewNode(NodeStyle style, String item, int count, int model, List<String> modelStrings,
                        float scale, boolean itemTooltip, boolean fake) {
        super(style);
        this.item = item == null ? "minecraft:paper" : item;
        this.count = Math.max(1, Math.min(64, count));
        this.model = model;
        this.modelStrings = modelStrings == null ? List.of() : List.copyOf(modelStrings);
        this.scale = scale <= 0f ? 1f : scale;
        this.itemTooltip = itemTooltip;
        this.fake = fake;
    }

    public String item() {
        return item;
    }

    public int count() {
        return count;
    }

    /** CustomModelData（float[0]）；-1 表示未设置。 */
    public int model() {
        return model;
    }

    public List<String> modelStrings() {
        return modelStrings;
    }

    public float scale() {
        return scale;
    }

    public boolean itemTooltip() {
        return itemTooltip;
    }

    public boolean fake() {
        return fake;
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
        width = resolvedW >= 0 ? resolvedW : 16f * scale;
        height = resolvedH >= 0 ? resolvedH : 16f * scale;
    }

    @Override
    public boolean hasTooltip() {
        return itemTooltip || super.hasTooltip();
    }
}
