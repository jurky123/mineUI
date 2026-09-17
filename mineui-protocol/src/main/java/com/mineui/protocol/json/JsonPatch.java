package com.mineui.protocol.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.PatchOp;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON Patch 应用器（RFC 6902 子集：add / replace / remove）。
 * <p>
 * 支持对象键与数组下标路径，JSON Pointer 转义（~1 → /，~0 → ~）。
 * 任何非法路径/越界/缺失目标均抛 {@link ProtocolException}，由调用方决定是否重新同步。
 */
public final class JsonPatch {

    private JsonPatch() {
    }

    public static void apply(JsonObject root, List<PatchOp> ops) throws ProtocolException {
        for (PatchOp op : ops) {
            applyOp(root, op);
        }
    }

    private static void applyOp(JsonObject root, PatchOp op) throws ProtocolException {
        if (op.op() == null || op.path() == null) {
            throw new ProtocolException("patch 操作缺少 op/path");
        }
        List<String> tokens = parsePointer(op.path());
        JsonElement parent = root;
        for (int i = 0; i < tokens.size() - 1; i++) {
            parent = child(parent, tokens.get(i), op.path());
        }
        String last = tokens.get(tokens.size() - 1);

        switch (op.op()) {
            case "replace" -> {
                requireValue(op);
                if (parent instanceof JsonObject obj) {
                    if (!obj.has(last)) {
                        throw missing(op.path());
                    }
                    obj.add(last, op.value());
                } else if (parent instanceof JsonArray arr) {
                    arr.set(indexInRange(last, arr.size(), op.path()), op.value());
                } else {
                    throw missing(op.path());
                }
            }
            case "add" -> {
                requireValue(op);
                if (parent instanceof JsonObject obj) {
                    obj.add(last, op.value());
                } else if (parent instanceof JsonArray arr) {
                    if ("-".equals(last)) {
                        arr.add(op.value());
                    } else {
                        insert(arr, indexForInsert(last, arr.size(), op.path()), op.value());
                    }
                } else {
                    throw missing(op.path());
                }
            }
            case "remove" -> {
                if (parent instanceof JsonObject obj) {
                    if (obj.remove(last) == null) {
                        throw missing(op.path());
                    }
                } else if (parent instanceof JsonArray arr) {
                    arr.remove(indexInRange(last, arr.size(), op.path()));
                } else {
                    throw missing(op.path());
                }
            }
            default -> throw new ProtocolException("不支持的 patch 操作: " + op.op());
        }
    }

    private static void requireValue(PatchOp op) throws ProtocolException {
        if (op.value() == null) {
            throw new ProtocolException("patch 操作 " + op.op() + " 缺少 value: " + op.path());
        }
    }

    /** Gson 的 JsonArray 没有按位插入，这里手动右移元素。 */
    private static void insert(JsonArray array, int index, JsonElement value) {
        array.add(value);
        for (int i = array.size() - 1; i > index; i--) {
            array.set(i, array.get(i - 1));
        }
        array.set(index, value);
    }

    private static JsonElement child(JsonElement current, String token, String path) throws ProtocolException {
        if (current instanceof JsonObject obj) {
            if (!obj.has(token)) {
                throw missing(path);
            }
            return obj.get(token);
        }
        if (current instanceof JsonArray arr) {
            return arr.get(indexInRange(token, arr.size(), path));
        }
        throw new ProtocolException("路径穿过非容器节点: " + path);
    }

    private static List<String> parsePointer(String pointer) throws ProtocolException {
        if (!pointer.startsWith("/") || pointer.length() < 2) {
            throw new ProtocolException("非法 JSON Pointer: " + pointer);
        }
        String[] raw = pointer.substring(1).split("/", -1);
        List<String> tokens = new ArrayList<>(raw.length);
        for (String token : raw) {
            if (token.isEmpty()) {
                throw new ProtocolException("JSON Pointer 含空键: " + pointer);
            }
            tokens.add(unescape(token));
        }
        return tokens;
    }

    private static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private static int indexInRange(String token, int size, String path) throws ProtocolException {
        int index = parseIndex(token, path);
        if (index < 0 || index >= size) {
            throw new ProtocolException("数组下标越界: " + path + " (size=" + size + ")");
        }
        return index;
    }

    private static int indexForInsert(String token, int size, String path) throws ProtocolException {
        int index = parseIndex(token, path);
        if (index < 0 || index > size) {
            throw new ProtocolException("数组插入下标越界: " + path + " (size=" + size + ")");
        }
        return index;
    }

    private static int parseIndex(String token, String path) throws ProtocolException {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new ProtocolException("非法数组下标: " + path + " (" + token + ")");
        }
    }

    private static ProtocolException missing(String path) {
        return new ProtocolException("路径目标不存在: " + path);
    }
}
