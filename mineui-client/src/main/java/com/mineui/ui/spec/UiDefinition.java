package com.mineui.ui.spec;

import com.mineui.ui.tree.UiNode;

/**
 * 一份界面定义。
 *
 * @param app    业务命名空间
 * @param view   视图名
 * @param source 来源描述（"mod" 或 "dev:<path>"）
 * @param root   根节点
 */
public record UiDefinition(String app, String view, String source, UiNode root) {
}
