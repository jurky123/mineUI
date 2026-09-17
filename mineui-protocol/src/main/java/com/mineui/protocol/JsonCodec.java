package com.mineui.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;

/** 载荷 JSON 编解码。Gson 由运行环境提供（Paper / Minecraft 客户端）。 */
public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonCodec() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static byte[] encode(Object value) {
        return toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    /** 任意值转 JSON 树（用于 PATCH value）。 */
    public static JsonElement toJsonTree(Object value) {
        return GSON.toJsonTree(value);
    }

    public static <T> T decode(byte[] utf8, Class<T> type) throws ProtocolException {
        if (utf8 == null) {
            throw new ProtocolException("JSON 载荷为 null");
        }
        try {
            return GSON.fromJson(new String(utf8, StandardCharsets.UTF_8), type);
        } catch (JsonSyntaxException e) {
            throw new ProtocolException("JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
