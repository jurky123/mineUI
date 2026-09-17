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
    private final boolean zoomable;
    private final double zoomMin;
    private final double zoomMax;

    /** 本地缩放（纯客户端，仅影响渲染，不影响布局；重开界面复位）。 */
    private double zoom = 1.0;

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse) {
        this(style, player, scale, followMouse, null, null, false, 0.5, 2.0);
    }

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse,
                          String skinValue, String skinSignature) {
        this(style, player, scale, followMouse, skinValue, skinSignature, false, 0.5, 2.0);
    }

    public PlayerViewNode(NodeStyle style, String player, float scale, boolean followMouse,
                          String skinValue, String skinSignature,
                          boolean zoomable, double zoomMin, double zoomMax) {
        super(style);
        this.player = player == null || player.isEmpty() ? "@self" : player;
        this.scale = scale <= 0f ? 30f : scale;
        this.followMouse = followMouse;
        this.skinValue = skinValue == null || skinValue.isEmpty() ? null : skinValue;
        this.skinSignature = skinSignature == null || skinSignature.isEmpty() ? null : skinSignature;
        this.zoomable = zoomable;
        this.zoomMin = zoomMin <= 0 ? 0.5 : zoomMin;
        this.zoomMax = Math.max(this.zoomMin + 0.01, zoomMax);
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

    public boolean zoomable() {
        return zoomable;
    }

    public double zoom() {
        return zoom;
    }

    /** 本地缩放：滚轮上滚放大（每格 ±10%），钳制在 [zoomMin, zoomMax]。 */
    public void zoomBy(double delta) {
        zoom = Math.max(zoomMin, Math.min(zoomMax, zoom * (1 + delta * 0.1)));
    }

    /** 命中自身时消费滚轮做缩放（不发 ACTION、不冒泡）。 */
    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (!visibleNow() || !contains(mx, my)) {
            return false;
        }
        for (UiNode child : childrenByZDesc()) {
            if (child.scroll(mx, my, amount)) {
                return true;
            }
        }
        if (!zoomable) {
            return false;
        }
        zoomBy(amount);
        return true;
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
