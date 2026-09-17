package com.mineui.protocol.msg;

/**
 * 服务端 → 客户端：打开界面。信封 SESSION 字段即会话 id。
 *
 * @param app  业务命名空间（如 "mineui"、"mineuno"）
 * @param view 视图名（如 "test"）
 */
public record Open(String app, String view) {
}
