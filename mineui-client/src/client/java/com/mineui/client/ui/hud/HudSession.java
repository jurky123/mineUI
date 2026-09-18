package com.mineui.client.ui.hud;

import com.mineui.client.MineUiConfig;
import com.mineui.client.ui.UiStateStore;
import com.mineui.client.ui.render.RenderCache;
import com.mineui.client.ui.render.UiTreeRenderer;
import com.mineui.protocol.msg.HudLayout;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import com.mineui.ui.util.HudAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 单个 HUD 会话：自己的状态、节点树、布局与渲染缓存。 */
final class HudSession {

    private final int id;
    private final String app;
    private final String view;
    private final String source;
    private final UiNode root;
    private final HudLayout serverLayout;
    private final UiStateStore state = new UiStateStore();
    private final RenderCache renderCache = new RenderCache();

    private int lastGeneration = -1;

    HudSession(int id, String app, String view, String source, UiNode root, HudLayout layout) {
        this.id = id;
        this.app = app;
        this.view = view;
        this.source = source;
        this.root = root;
        this.serverLayout = layout;
    }

    int id() {
        return id;
    }

    String app() {
        return app;
    }

    String view() {
        return view;
    }

    /** 服务端默认可见性（客户端本地开关优先）。 */
    boolean serverVisible() {
        return serverLayout.visible();
    }

    /** 同屏渲染顺序（小的先画）。 */
    int z() {
        return serverLayout.z();
    }

    UiStateStore state() {
        return state;
    }

    void render(GuiGraphicsExtractor graphics, Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        float screenWidth = minecraft.getWindow().getGuiScaledWidth();
        float screenHeight = minecraft.getWindow().getGuiScaledHeight();

        if (state.generation() != lastGeneration) {
            relayout(screenWidth, screenHeight, font);
            lastGeneration = state.generation();
        }

        HudLayout layout = MineUiConfig.get().effectiveLayout(app, view, serverLayout);
        float[] position = HudAnchor.resolve(layout.anchor(), layout.offsetX(), layout.offsetY(),
                root.width(), root.height(), screenWidth, screenHeight);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(position[0], position[1]);
        if (Math.abs(layout.scale() - 1f) > 0.001f) {
            pose.scale(layout.scale(), layout.scale());
        }
        new UiTreeRenderer(graphics, font, state, renderCache).render(root, 0, 0);
        pose.popMatrix();
    }

    private void relayout(float screenWidth, float screenHeight, Font font) {
        TextMeasurer measurer = new TextMeasurer() {
            @Override
            public int width(String text) {
                return font.width(text);
            }

            @Override
            public int lineHeight() {
                return font.lineHeight;
            }
        };
        // 以屏幕为视口度量；HUD 自身尺寸由内容决定
        root.measure(new MeasureContext(screenWidth, screenHeight, screenWidth, screenHeight, measurer, state));
        root.layout(0, 0);
    }
}
