package com.mineui.protocol.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态树点分路径读写（纯函数，便于单测）。
 * <p>
 * 与客户端 {@code {state.a.b}} 读取保持一致：点分即嵌套对象。
 * 注意：顶层字段名本身含 {@code .} 的旧写法会改变语义（见 {@link #set}）。
 */
public final class JsonPointers {

    private JsonPointers() {
    }

    /** set() 的结果：叶子此前是否存在 + 完整 pointer。 */
    public record SetResult(boolean existed, String pointer, JsonElement previous) {
    }

    /**
     * 按点分路径写入叶子值，中间缺失的对象自动创建。
     *
     * @param root  状态根对象（会被修改）
     * @param path 点分路径，如 {@code "player.name"}；单段等价于顶层字段
     * @param value 新值
     * @return 叶子此前是否存在、完整 pointer 与旧值
     * @throws IllegalArgumentException 路径为空、含空段，或中间段已是标量/数组时
     */
    public static SetResult set(JsonObject root, String path, JsonElement value) {
        List<String> segments = split(path);
        JsonObject parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            JsonElement child = parent.get(segment);
            if (child == null || child.isJsonNull()) {
                JsonObject created = new JsonObject();
                parent.add(segment, created);
                parent = created;
            } else if (child.isJsonObject()) {
                parent = child.getAsJsonObject();
            } else {
                throw new IllegalArgumentException("state 路径冲突：'" + segment
                        + "' 已是标量/数组，无法下钻 (" + path + ")");
            }
        }
        String leaf = segments.get(segments.size() - 1);
        JsonElement previous = parent.get(leaf);
        boolean existed = previous != null && !previous.isJsonNull();
        parent.add(leaf, value);
        return new SetResult(existed, pointer(segments), previous);
    }

    /** 按点分路径读取叶子（无则返回 null），与写入语义一致。 */
    public static JsonElement get(JsonObject root, String path) {
        List<String> segments = split(path);
        JsonElement current = root;
        for (String segment : segments) {
            if (!(current instanceof JsonObject object) || !object.has(segment)) {
                return null;
            }
            current = object.get(segment);
        }
        return current;
    }

    /** 完整 JSON Pointer（逐段转义）。 */
    public static String pointer(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            builder.append('/').append(escape(segment));
        }
        return builder.toString();
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static List<String> split(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("state 路径不能为空");
        }
        String[] parts = path.split("\\.", -1);
        List<String> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("state 路径含空段: " + path);
            }
            segments.add(part);
        }
        return segments;
    }
}
