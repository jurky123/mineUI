package com.mineui.ui.spec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.BoxNode;
import com.mineui.ui.tree.ColumnNode;
import com.mineui.ui.tree.ContainerNode;
import com.mineui.ui.tree.CrossAlign;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.GridNode;
import com.mineui.ui.tree.ImageNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.MainAlign;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.RowNode;
import com.mineui.ui.tree.ScrollViewNode;
import com.mineui.ui.tree.StackNode;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;

/** JSON → UI 节点树。 */
public final class UiSpecParser {

    private UiSpecParser() {
    }

    public static UiNode parse(JsonObject json) throws UiSpecException {
        String type = requireString(json, "type");
        NodeStyle style = parseStyle(json);

        UiNode node = switch (type) {
            case "column" -> new ColumnNode(style);
            case "row" -> new RowNode(style);
            case "stack" -> new StackNode(style);
            case "text" -> new TextNode(style,
                    optString(json, "text", ""),
                    parseColor(json.get("color"), 0xFFFFFFFF),
                    optFloat(json, "scale", 1f));
            case "button" -> new ButtonNode(style,
                    optString(json, "text", ""),
                    optString(json, "action", ""),
                    parseColor(json.get("background"), 0xFF3B3B3B),
                    parseColor(json.get("hoverBackground"), 0xFF4F4F4F),
                    parseColor(json.get("color"), 0xFFFFFFFF));
            case "box", "spacer" -> new BoxNode(style);
            case "image" -> parseImage(json, style);
            case "scroll" -> new ScrollViewNode(style);
            case "grid" -> new GridNode(style,
                    optInt(json, "columns", 2),
                    optFloat(json, "rowGap", optFloat(json, "gap", 0f)));
            case "item" -> new ItemViewNode(style,
                    optString(json, "item", "minecraft:paper"),
                    optInt(json, "count", 1),
                    optInt(json, "model", -1),
                    parseStringList(json, "modelStrings"),
                    optFloat(json, "scale", 1f),
                    optBool(json, "itemTooltip", false),
                    optBool(json, "fake", false));
            case "entity" -> new EntityViewNode(style,
                    requireString(json, "entity"),
                    optFloat(json, "scale", 30f),
                    optBool(json, "followMouse", true),
                    optFloat(json, "yaw", 0f),
                    optFloat(json, "bodyYaw", optFloat(json, "yaw", 0f)),
                    optFloat(json, "pitch", 0f));
            case "player" -> parsePlayer(json, style);
            default -> throw new UiSpecException("未知节点类型: " + type);
        };

        if (json.has("children") && !json.get("children").isJsonNull()) {
            if (!(node instanceof ContainerNode container)) {
                throw new UiSpecException("节点 " + type + " 不支持 children");
            }
            JsonArray children = json.getAsJsonArray("children");
            for (JsonElement child : children) {
                if (!child.isJsonObject()) {
                    throw new UiSpecException("children 元素必须是对象");
                }
                container.addChild(parse(child.getAsJsonObject()));
            }
        }
        return node;
    }

    private static NodeStyle parseStyle(JsonObject json) throws UiSpecException {
        Integer background = json.has("background") && !json.get("background").isJsonNull()
                ? parseColor(json.get("background"), 0)
                : null;

        Integer gradientTo = null;
        if (json.has("gradient") && !json.get("gradient").isJsonNull()) {
            JsonArray gradient = json.getAsJsonArray("gradient");
            if (gradient.size() != 2) {
                throw new UiSpecException("gradient 必须是 [起始色, 结束色]");
            }
            if (background == null) {
                background = parseColor(gradient.get(0), 0);
            }
            gradientTo = parseColor(gradient.get(1), 0);
        }

        Integer borderColor = null;
        float borderWidth = 0f;
        if (json.has("border") && json.get("border").isJsonObject()) {
            JsonObject border = json.getAsJsonObject("border");
            borderColor = parseColor(border.get("color"), 0xFF000000);
            borderWidth = border.has("width") ? border.get("width").getAsFloat() : 1f;
        }

        Integer shadowColor = null;
        float shadowSize = 0f;
        float shadowOffsetY = 0f;
        if (json.has("shadow") && json.get("shadow").isJsonObject()) {
            JsonObject shadow = json.getAsJsonObject("shadow");
            shadowColor = parseColor(shadow.get("color"), 0x80000000);
            shadowSize = shadow.has("size") ? shadow.get("size").getAsFloat() : 4f;
            shadowOffsetY = shadow.has("offsetY") ? shadow.get("offsetY").getAsFloat() : 2f;
        }

        return new NodeStyle(
                optString(json, "id", null),
                SizeSpec.parse(json.get("width")),
                SizeSpec.parse(json.get("height")),
                Insets.parse(json.get("padding")),
                background,
                CrossAlign.parse(optString(json, "align", null), CrossAlign.START),
                MainAlign.parse(optString(json, "justify", null), MainAlign.START),
                optFloat(json, "gap", 0f),
                optFloat(json, "radius", 0f),
                borderColor,
                borderWidth,
                shadowColor,
                shadowSize,
                shadowOffsetY,
                gradientTo,
                optInt(json, "z", 0),
                optBool(json, "clip", false),
                optBool(json, "pulse", false),
                BooleanSpec.parse(json.get("visible")),
                optString(json, "tooltip", null),
                optBool(json, "modal", false),
                optString(json, "action", null));
    }

    /**
     * 玩家 3D 预览：
     * <ul>
     *   <li>{@code player}：{@code "@self"} 或在线玩家名；</li>
     *   <li>{@code skin}：{@code {"value": "{state.x}", "signature": "{state.y}"}} 任意皮肤属性
     *       （服务端下发 value/signature，客户端直接按该皮肤渲染，无需玩家在线）。</li>
     * </ul>
     */
    private static PlayerViewNode parsePlayer(JsonObject json, NodeStyle style) throws UiSpecException {
        String skinValue = null;
        String skinSignature = null;
        if (json.has("skin") && json.get("skin").isJsonObject()) {
            JsonObject skin = json.getAsJsonObject("skin");
            skinValue = optString(skin, "value", null);
            skinSignature = optString(skin, "signature", null);
            if (skinValue == null || skinValue.isEmpty()) {
                throw new UiSpecException("player.skin 缺少 value");
            }
        }
        return new PlayerViewNode(style,
                optString(json, "player", "@self"),
                optFloat(json, "scale", 30f),
                optBool(json, "followMouse", true),
                skinValue, skinSignature);
    }

    private static ImageNode parseImage(JsonObject json, NodeStyle style) throws UiSpecException {
        String texture = requireString(json, "texture");
        float textureWidth = 256;
        float textureHeight = 256;
        if (json.has("textureSize")) {
            JsonArray size = json.getAsJsonArray("textureSize");
            if (size.size() != 2) {
                throw new UiSpecException("textureSize 必须是 [宽, 高]");
            }
            textureWidth = size.get(0).getAsFloat();
            textureHeight = size.get(1).getAsFloat();
        }
        return new ImageNode(style, texture,
                optFloat(json, "u", 0f),
                optFloat(json, "v", 0f),
                optFloat(json, "regionWidth", 16f),
                optFloat(json, "regionHeight", 16f),
                textureWidth, textureHeight);
    }

    public static int parseColor(JsonElement element, int fallback) throws UiSpecException {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        String text = element.getAsString().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        try {
            long value = Long.parseLong(text, 16);
            if (text.length() <= 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            throw new UiSpecException("无法解析颜色: " + element);
        }
    }

    private static String requireString(JsonObject json, String key) throws UiSpecException {
        String value = optString(json, key, null);
        if (value == null || value.isEmpty()) {
            throw new UiSpecException("缺少字段: " + key);
        }
        return value;
    }

    private static String optString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static float optFloat(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private static boolean optBool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    private static java.util.List<String> parseStringList(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return java.util.List.of();
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            result.add(item.getAsString());
        }
        return result;
    }
}
