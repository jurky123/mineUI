package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * 客户端 → 服务端：用户操作。
 * 信封 SESSION / REVISION 字段携带客户端当前会话与修订号。
 *
 * @param id      动作 id（如 "button_click"）
 * @param payload 动作参数（可空）
 */
public record Action(String id, JsonObject payload) {

    public Action {
        payload = payload == null ? new JsonObject() : payload;
    }

    public Action(String id) {
        this(id, new JsonObject());
    }
}
