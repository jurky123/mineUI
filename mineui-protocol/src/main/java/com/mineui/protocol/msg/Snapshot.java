package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * 服务端 → 客户端：完整状态。信封 REVISION 字段即修订号。
 *
 * @param state 完整状态对象（顶层字段由业务定义）
 */
public record Snapshot(JsonObject state) {

    public Snapshot {
        state = state == null ? new JsonObject() : state;
    }
}
