package com.mineui.ui.tree;

/** 状态代数来源：状态每成功应用一次自增，用于缓存失效与重布局。 */
public interface GenerationSource {

    int generation();
}
