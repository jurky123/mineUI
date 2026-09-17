package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮换规格：按本地时间在颜色或物品之间循环（纯客户端，不耗网络）。
 * <p>
 * JSON：
 * <pre>
 * { "cycle": { "interval": 1.0, "colors": ["#FF5555", "#55FF55"] } }          // 背景色轮换
 * { "cycle": { "interval": 1.0, "items": ["minecraft:red_wool", "..."] } }     // 物品轮换
 * </pre>
 */
public record CycleSpec(float intervalSeconds, List<Integer> colors, List<String> items) {

    public CycleSpec {
        colors = colors == null ? List.of() : List.copyOf(colors);
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 解析；无有效内容返回 null。 */
    public static CycleSpec parse(JsonElement element) throws UiSpecException {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        float interval = json.has("interval") ? json.get("interval").getAsFloat() : 1f;
        if (interval < 0.05f) {
            interval = 0.05f;
        }
        List<Integer> colors = new ArrayList<>();
        if (json.has("colors") && !json.get("colors").isJsonNull()) {
            for (JsonElement color : json.getAsJsonArray("colors")) {
                colors.add(UiSpecParser.parseColor(color, 0xFFFFFFFF));
            }
        }
        List<String> items = new ArrayList<>();
        if (json.has("items") && !json.get("items").isJsonNull()) {
            for (JsonElement item : json.getAsJsonArray("items")) {
                items.add(item.getAsString());
            }
        }
        if (colors.isEmpty() && items.isEmpty()) {
            return null;
        }
        return new CycleSpec(interval, colors, items);
    }

    /** 当前应显示的颜色（colors 为空时返回 fallback）。 */
    public int colorAt(long millis, int fallback) {
        if (colors.isEmpty()) {
            return fallback;
        }
        int index = (int) ((millis / (long) (intervalSeconds * 1000f)) % colors.size());
        return colors.get(index);
    }

    /** 当前应显示的物品 id（items 为空时返回 fallback）。 */
    public String itemAt(long millis, String fallback) {
        if (items.isEmpty()) {
            return fallback;
        }
        int index = (int) ((millis / (long) (intervalSeconds * 1000f)) % items.size());
        return items.get(index);
    }
}
