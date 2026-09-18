package com.mineui.ui.tree;

import com.google.gson.JsonElement;

/** 状态读取（绑定解析用；客户端由 UiStateStore 实现）。 */
public interface StateAccess {

    StateAccess EMPTY = (path, defaultValue) -> defaultValue;

    String get(String path, String defaultValue);

    /** 读取原始 JSON（列表 items 等结构绑定用）；默认无。 */
    default JsonElement getElement(String path) {
        return null;
    }
}
