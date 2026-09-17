package com.mineui.client.ui;

import com.mineui.client.net.ProtocolClient;
import com.mineui.client.ui.anim.AnimationController;
import com.mineui.client.ui.render.UiPainter;
import com.mineui.client.ui.render.UiTreeRenderer;
import com.mineui.ui.anim.Easing;
import com.mineui.ui.spec.UiDefinition;
import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 MineUI 界面：节点树来自客户端 UI JSON，数据来自服务端 state。
 * 支持 F9 热重载、悬停过渡、点击/状态脉冲动画。
 */
public final class UiScreen extends Screen {

    private static final int BACKGROUND = 0xC0080C10;
    private static final float HOVER_SPEED = 14f;
    private static final float MAX_FRAME_DELTA = 0.1f;
    private static final long TOOLTIP_DELAY_NANOS = 350_000_000L;

    private final String app;
    private final String view;
    private final String source;
    private final UiNode root;
    private final TextMeasurer measurer;
    private final AnimationController animations = new AnimationController();
    private final List<UiNode> pulseNodes = new ArrayList<>();

    private int lastRevision = -1;
    private long lastFrameNanos;
    private UiNode tooltipNode;
    private long tooltipSinceNanos;

    public UiScreen(UiDefinition definition) {
        super(Component.literal("MineUI " + definition.app() + "/" + definition.view()));
        this.app = definition.app();
        this.view = definition.view();
        this.source = definition.source();
        this.root = definition.root();
        this.measurer = new TextMeasurer() {
            @Override
            public int width(String text) {
                return font.width(text);
            }

            @Override
            public int lineHeight() {
                return font.lineHeight;
            }
        };
    }

    public String app() {
        return app;
    }

    public String view() {
        return view;
    }

    @Override
    protected void init() {
        relayout();
        pulseNodes.clear();
        root.collectPulses(pulseNodes);
        lastRevision = ProtocolClient.state().revision();
    }

    /** 状态变化/窗口尺寸变化后重新度量与布局（可见性、文本长度等会影响尺寸）。 */
    private void relayout() {
        MeasureContext context = new MeasureContext(width, height, width, height, measurer, ProtocolClient.state());
        root.measure(context);
        root.layout(0, 0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        float delta = frameDelta();
        updateHover(root, delta);

        int revision = ProtocolClient.state().revision();
        if (revision != lastRevision) {
            lastRevision = revision;
            relayout();
            for (UiNode node : pulseNodes) {
                pulse(node);
            }
        }
        animations.update(delta);

        graphics.text(this.font, source, 6, 6, 0xFF606060, false);
        new UiTreeRenderer(graphics, this.font, ProtocolClient.state()).render(root);
        drawTooltip(graphics, mouseX, mouseY);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiNode node = tooltipNode;
        if (node == null || !node.visibleNow() || node.style().tooltip() == null) {
            return;
        }
        if (System.nanoTime() - tooltipSinceNanos < TOOLTIP_DELAY_NANOS) {
            return;
        }
        String text = Bindings.resolve(node.style().tooltip(), ProtocolClient.state());
        if (text.isEmpty()) {
            return;
        }
        int textWidth = font.width(text);
        float x = mouseX + 10f;
        float y = mouseY + 10f;
        if (x + textWidth + 8f > width) {
            x = width - textWidth - 8f;
        }
        if (y + font.lineHeight + 6f > height) {
            y = height - font.lineHeight - 6f;
        }
        new UiPainter(graphics).fillRounded(x - 4f, y - 3f, textWidth + 8f, font.lineHeight + 6f, 4f, 0xF0101018, 1f);
        graphics.text(font, text, Math.round(x), Math.round(y), 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        UiNode hit = root.mouseClicked(event.x(), event.y(), event.button());
        if (hit instanceof ButtonNode button) {
            playClickPop(button);
            if (!button.action().isEmpty()) {
                ProtocolClient.sendAction(button.action());
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root.mouseMoved(mouseX, mouseY);
        UiNode target = root.findTooltip(mouseX, mouseY);
        if (target != tooltipNode) {
            tooltipNode = target;
            tooltipSinceNanos = System.nanoTime();
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        tooltipNode = null;
        if (root.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F9) {
            MineUiScreens.reload();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ProtocolClient.sendClose();
        MineUiScreens.clientClosed(this);
        super.onClose();
    }

    // ---------- 动画 ----------

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(delta, MAX_FRAME_DELTA);
    }

    private void updateHover(UiNode node, float delta) {
        if (node instanceof ButtonNode) {
            float target = node.hovered() ? 1f : 0f;
            float current = node.hoverProgress();
            node.setHoverProgress(current + (target - current) * Math.min(1f, delta * HOVER_SPEED));
        }
        for (UiNode child : node.children()) {
            updateHover(child, delta);
        }
    }

    private void playClickPop(UiNode node) {
        animations.animate(node, AnimationController.Property.SCALE, 1f, 1.06f, 0.06f, Easing.EASE_OUT,
                () -> animations.animate(node, AnimationController.Property.SCALE, 1.06f, 1f, 0.16f, Easing.BACK_OUT));
    }

    private void pulse(UiNode node) {
        animations.animate(node, AnimationController.Property.SCALE, 1f, 1.25f, 0.08f, Easing.EASE_OUT,
                () -> animations.animate(node, AnimationController.Property.SCALE, 1.25f, 1f, 0.24f, Easing.BACK_OUT));
    }
}
