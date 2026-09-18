package com.mineui.ui.spec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * 原版风格皮肤预设：业务页面只需写 {@code "skin": "vanilla:button"}，
 * 由预设补齐原版九宫格贴图/尺寸/颜色，JSON 里显式写的字段优先。
 */
public final class VanillaSkins {

    private static final Map<String, JsonObject> SKINS = new HashMap<>();

    static {
        put("vanilla:button", """
                { "height": 20, "color": "#FFFFFF",
                  "sprite": "minecraft:widget/button",
                  "spriteHover": "minecraft:widget/button_highlighted" }
                """);
        put("vanilla:input", """
                { "height": 20, "color": "#E0E0E0", "placeholderColor": "#707070",
                  "sprite": "minecraft:widget/text_field",
                  "spriteFocus": "minecraft:widget/text_field_highlighted" }
                """);
        put("vanilla:panel", """
                { "sprite": "minecraft:popup/background", "padding": 12 }
                """);
        put("vanilla:dialog", """
                { "sprite": "minecraft:popup/background", "padding": 12, "modal": true }
                """);
        put("vanilla:slider", """
                { "height": 20, "color": "#FFFFFF" }
                """);
        put("vanilla:slot", """
                { "sprite": "minecraft:widget/slot_frame" }
                """);
        // HUD/浮层用：无边框半透明圆角（程序化绘制，无需素材）
        put("mineui:glass", """
                { "background": "#8A0E141B", "radius": 6, "padding": 8 }
                """);
        put("mineui:glass_dense", """
                { "background": "#A00B1017", "radius": 6, "padding": 10 }
                """);
    }

    private VanillaSkins() {
    }

    /** 应用皮肤：返回合并后的新对象；无皮肤时原样返回。 */
    public static JsonObject apply(JsonObject json) {
        JsonElement skinElement = json.get("skin");
        // PlayerView 等节点用 "skin" 对象承载皮肤属性，只有字符串才是皮肤预设
        if (skinElement == null || skinElement.isJsonNull() || !skinElement.isJsonPrimitive()) {
            return json;
        }
        String skin = skinElement.getAsString();
        if (skin == null || skin.isEmpty()) {
            return json;
        }
        if (skin.indexOf(':') < 0) {
            if ("vanilla".equals(skin)) {
                String type = json.has("type") ? json.get("type").getAsString() : "";
                skin = "vanilla:" + type;
            } else {
                skin = "mineui:" + skin;
            }
        }
        JsonObject defaults = SKINS.get(skin);
        if (defaults == null) {
            return json;
        }
        JsonObject merged = defaults.deepCopy();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static void put(String skin, String json) {
        SKINS.put(skin, JsonParser.parseString(json).getAsJsonObject());
    }
}
