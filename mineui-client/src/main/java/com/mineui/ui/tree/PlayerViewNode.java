package com.mineui.ui.tree;

/**
 * 玩家 3D 预览节点。
 * {@code player} 取 "@self"（自己）或在线玩家名；在线但不在视野内的玩家会用玩家列表皮肤。
 */
public final class PlayerViewNode extends UiNode {

    private final String player;
    private final float scale;
    private final boolean followMouse;

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse) {
        super(style);
        this.player = player == null || player.isEmpty() ? "@self" : player;
        this.scale = scale <= 0f ? 30f : scale;
        this.followMouse = followMouse;
    }

    public String player() {
        return player;
    }

    public float scale() {
        return scale;
    }

    public boolean followMouse() {
        return followMouse;
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
