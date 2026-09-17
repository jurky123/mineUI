package com.mineui.ui.tree;

/** 状态读取（绑定解析用；客户端由 UiStateStore 实现）。 */
public interface StateAccess {

    StateAccess EMPTY = (path, defaultValue) -> defaultValue;

    String get(String path, String defaultValue);
}
