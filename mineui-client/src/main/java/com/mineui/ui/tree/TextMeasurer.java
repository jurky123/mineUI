package com.mineui.ui.tree;

/** 文本测量（客户端用 Font 实现，单测用假实现）。 */
public interface TextMeasurer {

    int width(String text);

    int lineHeight();
}
