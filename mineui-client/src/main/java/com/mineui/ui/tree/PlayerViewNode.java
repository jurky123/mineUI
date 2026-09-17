package com.mineui.ui.tree;

/**
 * 玩家 3D 预览节点。
 * <p>
 * 两种皮肤来源：
 * <ul>
 *   <li>{@code player}：{@code "@self"}（自己）或在线玩家名；在线但不在视野内的玩家会用玩家列表皮肤。</li>
 *   <li>{@code skin.value}/{@code skin.signature}：任意皮肤属性模板（如 {@code "{state.preview.value}"}），
 *       即使该皮肤不属于任何在线玩家也能渲染。</li>
 * </ul>
 */
public final class PlayerViewNode extends UiNode {

    private final String player;
    private final float scale;
    private final boolean followMouse;
    private final String skinValue;
    private final String skinSignature;

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse) {
        this(style, player, scale, followMouse, null, null);
    }

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse,
                          String skinValue, String skinSignature) {
        super(style);
        this.player = player == null || player.isEmpty() ? "@self" : player;
        this.scale = scale <= 0f ? 30f : scale;
        this.followMouse = followMouse;
        this.skinValue = skinValue == null || skinValue.isEmpty() ? null : skinValue;
        this.skinSignature = skinSignature == null || skinSignature.isEmpty() ? null : skinSignature;
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

    /** 皮肤 value 模板（null 表示按在线玩家名渲染）。 */
    public String skinValue() {
        return skinValue;
    }

    /** 皮肤 signature 模板（可空）。 */
    public String skinSignature() {
        return skinSignature;
    }

    public boolean hasSkin() {
        return skinValue != null;
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
