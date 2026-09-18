package com.mineui.ui.tree;

/** 可含子节点的容器基类。 */
public abstract class ContainerNode extends UiNode {

    protected ContainerNode(NodeStyle style) {
        super(style);
    }

    public void addChild(UiNode child) {
        addChildInternal(child);
    }

    protected void clearChildren() {
        clearChildrenInternal();
    }

    protected float gap() {
        return style().gap();
    }
}
