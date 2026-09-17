package com.mineui.ui.tree;

/**
 * 实体 3D 预览节点（生物实体；用假实体渲染，不真正生成）。
 */
public final class EntityViewNode extends UiNode {

    private final String entityType;
    private final float scale;
    private final boolean followMouse;
    private final float yaw;
    private final float bodyYaw;
    private final float pitch;

    public EntityViewNode(NodeStyle style, String entityType, float scale, boolean followMouse,
                          float yaw, float bodyYaw, float pitch) {
        super(style);
        this.entityType = entityType == null ? "" : entityType;
        this.scale = scale <= 0f ? 30f : scale;
        this.followMouse = followMouse;
        this.yaw = yaw;
        this.bodyYaw = bodyYaw;
        this.pitch = pitch;
    }

    public String entityType() {
        return entityType;
    }

    public float scale() {
        return scale;
    }

    public boolean followMouse() {
        return followMouse;
    }

    public float yaw() {
        return yaw;
    }

    public float bodyYaw() {
        return bodyYaw;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : 64f;
        height = resolvedH >= 0 ? resolvedH : 96f;
    }
}
