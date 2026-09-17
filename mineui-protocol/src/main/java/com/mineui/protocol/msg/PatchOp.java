package com.mineui.protocol.msg;

import com.google.gson.JsonElement;

/**
 * JSON Patch 单条操作（RFC 6902 子集：add / replace / remove）。
 *
 * @param op    操作名
 * @param path  JSON Pointer（如 "/count"、"/player/name"、"/cards/0"）
 * @param value 值（remove 时为 null）
 */
public record PatchOp(String op, String path, JsonElement value) {

    public static PatchOp replace(String path, JsonElement value) {
        return new PatchOp("replace", path, value);
    }

    public static PatchOp add(String path, JsonElement value) {
        return new PatchOp("add", path, value);
    }

    public static PatchOp remove(String path) {
        return new PatchOp("remove", path, null);
    }
}
