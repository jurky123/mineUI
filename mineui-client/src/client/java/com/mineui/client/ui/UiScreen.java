package com.mineui.client.ui;

import com.google.gson.JsonObject;
import com.mineui.client.net.ProtocolClient;
import com.mineui.client.ui.anim.AnimationController;
import com.mineui.client.ui.render.ItemStacks;
import com.mineui.client.ui.render.UiPainter;
import com.mineui.client.ui.render.UiTreeRenderer;
import com.mineui.ui.anim.Easing;
import com.mineui.ui.spec.UiDefinition;
import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.InputNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.SliderNode;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import com.mineui.ui.util.ActionThrottle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
    /** hoverAction 节流窗口：限速 8/s 的服务端不会被鼠标扫列表刷爆，最终状态仍会补发。 */
    private static final long HOVER_ACTION_INTERVAL_MILLIS = 200L;

    private final String app;
    private final String view;
    private final String source;
    private final UiNode root;
    private final TextMeasurer measurer;
    private final AnimationController animations = new AnimationController();
    private final List<UiNode> pulseNodes = new ArrayList<>();
    private final List<String> hoverActions = new ArrayList<>();
    private final ActionThrottle hoverThrottle = new ActionThrottle(HOVER_ACTION_INTERVAL_MILLIS);

    private int lastGeneration = -1;
    private long lastFrameNanos;
    private UiNode tooltipNode;
    private long tooltipSinceNanos;
    private InputNode focusedInput;
    private SliderNode draggingSlider;

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
        lastGeneration = ProtocolClient.state().generation();
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

        String pendingHover = hoverThrottle.poll();
        if (pendingHover != null) {
            ProtocolClient.sendAction(pendingHover);
        }

        int generation = ProtocolClient.state().generation();
        if (generation != lastGeneration) {
            lastGeneration = generation;
            relayout();
            for (UiNode node : pulseNodes) {
                pulse(node);
            }
        }
        animations.update(delta);

        graphics.text(this.font, source, 6, 6, 0xFF606060, false);
        new UiTreeRenderer(graphics, this.font, ProtocolClient.state()).render(root, mouseX, mouseY);
        drawTooltip(graphics, mouseX, mouseY);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {        UiNode node = tooltipNode;
        if (node == null || !node.visibleNow()) {
            return;
        }
        // 物品名提示：直接使用原版物品 tooltip
        if (node instanceof ItemViewNode itemNode && itemNode.itemTooltip() && node.style().tooltip() == null) {
            ItemStack stack = ItemStacks.resolve(itemNode);
            if (!stack.isEmpty()) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
            return;
        }
        if (node.style().tooltip() == null) {
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
        // 原版风格提示框：深色底 + 紫色上下边
        int left = Math.round(x) - 4;
        int right = Math.round(x) + textWidth + 4;
        int top = Math.round(y) - 3;
        int bottom = Math.round(y) + font.lineHeight + 3;
        graphics.fill(left, top, right, bottom, 0xF0100010);
        graphics.fill(left, top, right, top + 1, 0xFF5000FF);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF28007F);
        graphics.text(font, text, Math.round(x), Math.round(y), 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        UiNode hit = root.mouseClicked(event.x(), event.y(), event.button());
        if (hit instanceof InputNode input) {
            if (focusedInput != null && focusedInput != input) {
                focusedInput.blur();
            }
            input.focus();
            focusedInput = input;
            return true;
        }
        if (focusedInput != null) {
            focusedInput.blur();
            focusedInput = null;
        }
        if (hit instanceof SliderNode slider) {
            draggingSlider = slider;
            slider.beginDrag(event.x(), event.y());
            return true;
        }
        if (hit != null && hit.clickable()) {
            playClickPop(hit);
            ProtocolClient.sendAction(hit.action());
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root.mouseMoved(mouseX, mouseY);
        hoverActions.clear();
        root.collectHoverActions(hoverActions);
        for (String action : hoverActions) {
            String immediate = hoverThrottle.submit(action);
            if (immediate != null) {
                ProtocolClient.sendAction(immediate);
            }
        }
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSlider != null) {
            draggingSlider.dragTo(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null) {
            SliderNode slider = draggingSlider;
            Double value = slider.finishDrag();
            draggingSlider = null;
            if (value != null && !slider.action().isEmpty()) {
                JsonObject payload = new JsonObject();
                payload.addProperty("value", value);
                ProtocolClient.sendAction(slider.action(), payload);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F9) {
            MineUiScreens.reload();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F10) {
            MineUiScreens.exportDevTemplate();
            return true;
        }
        InputNode input = focusedInput;
        if (input != null && input.focused()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_ESCAPE -> {
                    input.blur();
                    focusedInput = null;
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    submitInput(input);
                    return true;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    input.backspace();
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    input.deleteForward();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    input.moveCursor(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    input.moveCursor(1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    input.moveCursorTo(0);
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    input.moveCursorTo(input.text().length());
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        InputNode input = focusedInput;
        if (input != null && input.focused() && event.isAllowedChatCharacter()) {
            if (input.insert(event.codepointAsString())) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    private void submitInput(InputNode input) {
        if (input.action().isEmpty()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("text", input.text());
        ProtocolClient.sendAction(input.action(), payload);
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
