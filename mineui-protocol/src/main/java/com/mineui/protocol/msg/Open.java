package com.mineui.protocol.msg;

import com.google.gson.JsonObject;

/**
 * 服务端 → 客户端：打开界面。信封 SESSION 字段即会话 id。
 *
 * @param app  业务命名空间（如 "mineui"、"mineuno"）
 * @param view 视图名（如 "test"）
 * @param ui   可选的界面定义（业务插件自带页面）；null 表示从客户端内置/开发目录加载
 */
public record Open(String app, String view, JsonObject ui) {

    public Open(String app, String view) {
        this(app, view, null);
    }
}
