package com.mineui.protocol.msg;

/**
 * 一条键位声明：客户端键位池的槽位 → 按键触发的全局动作。
 *
 * @param slot   客户端键位池槽位（"1".."8"）
 * @param action 按键时回传的全局动作 id
 * @param label  展示名（页面按键提示可用）
 */
public record Keybind(String slot, String action, String label) {

    public Keybind {
        slot = slot == null ? "" : slot;
        action = action == null ? "" : action;
        label = label == null ? "" : label;
    }
}
