package com.mineui.client.ui.render;

import com.mineui.ui.tree.ItemViewNode;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ItemView 节点的 ItemStack 构造与缓存。 */
public final class ItemStacks {

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private ItemStacks() {
    }

    public static ItemStack resolve(ItemViewNode node) {
        return resolve(node, node.item());
    }

    /** 按指定物品 id 构造（hoverItem / 轮换物品用）。 */
    public static ItemStack resolve(ItemViewNode node, String itemId) {
        return resolve(node, itemId, node.modelStrings());
    }

    /** 按解析后的物品 id 与 modelStrings 构造（状态绑定场景）。 */
    public static ItemStack resolve(ItemViewNode node, String itemId, List<String> resolvedModelStrings) {
        String key = itemId + "|" + node.model() + "|" + String.join(",", resolvedModelStrings) + "|" + node.count();
        return CACHE.computeIfAbsent(key, ignored -> build(node, itemId, resolvedModelStrings));
    }

    private static ItemStack build(ItemViewNode node, String itemId, List<String> resolvedModelStrings) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, node.count());
        CustomModelData model = null;
        if (node.model() >= 0) {
            model = new CustomModelData(List.of((float) node.model()), List.of(), List.of(), List.of());
        } else if (!resolvedModelStrings.isEmpty()) {
            model = new CustomModelData(List.of(), List.of(), resolvedModelStrings, List.of());
        }
        if (model != null) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, model);
        }
        return stack;
    }
}
