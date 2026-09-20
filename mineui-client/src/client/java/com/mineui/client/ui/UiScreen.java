package com.mineui.client.ui;

import com.google.gson.JsonObject;
import com.mineui.client.net.ProtocolClient;
import com.mineui.client.ui.anim.AnimationController;
import com.mineui.client.ui.render.ItemStacks;
import com.mineui.client.ui.render.RenderCache;
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
import com.mineui.ui.tree.TabsNode;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import com.mineui.ui.util.ActionThrottle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
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
    private final RenderCache renderCache = new RenderCache();

    private int lastGeneration = -1;
    private long lastLocalGeneration = Long.MIN_VALUE;
    private long lastFrameNanos;
    private UiNode tooltipNode;
    private long tooltipSinceNanos;
    private InputNode focusedInput;
    /** 按下中的按钮（按下态视觉；松开清除）。 */
    private ButtonNode pressedButton;
    /** IME 组词进行中：此时回车是确认组词，不能触发提交。 */
    private boolean imeComposing;
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
        lastLocalGeneration = com.mineui.client.ui.local.MineUiLocalBridge.generation();
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
        long localGeneration = com.mineui.client.ui.local.MineUiLocalBridge.generation();
        if (generation != lastGeneration || localGeneration != lastLocalGeneration) {
            lastGeneration = generation;
            lastLocalGeneration = localGeneration;
            relayout();
            for (UiNode node : pulseNodes) {
                pulse(node);
            }
        }
        animations.update(delta);

        graphics.text(this.font, source, 6, 6, 0xFF606060, false);
        new UiTreeRenderer(graphics, this.font, ProtocolClient.state(), renderCache).render(root, mouseX, mouseY);
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
            focusInput(input);
            return true;
        }
        blurInput();
        if (hit instanceof SliderNode slider) {
            draggingSlider = slider;
            slider.beginDrag(event.x(), event.y());
            return true;
        }
        if (hit != null && hit.clickable()) {
            playClickPop(hit);
            if (hit instanceof ButtonNode button) {
                button.setPressed(true);
                pressedButton = button;
            }
            ProtocolClient.sendAction(hit.action(), actionPayload(hit));
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /**
     * 动作负载：命中节点在列表条目内时携带条目下标 {@code {"index": n}}；
     * Tabs 控件值走独立的 {@code "tab"} 字段，不覆盖条目身份。
     */
    private static JsonObject actionPayload(UiNode node) {
        int index = node.enclosingItemIndex();
        int tab = node instanceof TabsNode tabs ? tabs.tabIndex() : -1;
        if (index < 0 && tab < 0) {
            return null;
        }
        JsonObject payload = new JsonObject();
        if (index >= 0) {
            payload.addProperty("index", index);
        }
        if (tab >= 0) {
            payload.addProperty("tab", tab);
        }
        return payload;
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
        if (pressedButton != null) {
            pressedButton.setPressed(false);
            pressedButton = null;
        }
        if (draggingSlider != null) {
            SliderNode slider = draggingSlider;
            Double value = slider.finishDrag();
            draggingSlider = null;
            if (value != null && !slider.action().isEmpty()) {
                JsonObject payload = new JsonObject();
                payload.addProperty("value", value);
                int index = slider.enclosingItemIndex();
                if (index >= 0) {
                    payload.addProperty("index", index);
                }
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
                    blurInput();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (imeComposing) {
                        // 组词进行中的回车是确认组词，不能触发提交（合成文本随后经 charTyped 写入）
                        return true;
                    }
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

    /** 输入框聚焦：打开 IME 并把候选窗定位到输入框（GUI 坐标，引擎内部换算窗口坐标）。 */
    private void focusInput(InputNode input) {
        if (focusedInput != null && focusedInput != input) {
            focusedInput.blur();
            focusedInput.clearPreedit();
        }
        input.focus();
        input.clearPreedit();
        focusedInput = input;
        imeComposing = false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.onTextInputFocusChange(this, true);
        int x0 = Math.round(input.x()) + 2;
        int y0 = Math.round(input.y());
        int x1 = Math.round(input.x() + input.width()) - 2;
        int y1 = Math.round(input.y() + input.height());
        minecraft.textInputManager().setTextInputArea(x0, y0, Math.max(x1, x0 + 1), Math.max(y1, y0 + 1));
    }

    /** 输入框失焦：关闭 IME 并清理组词状态。 */
    private void blurInput() {
        if (focusedInput != null) {
            focusedInput.blur();
            focusedInput.clearPreedit();
            focusedInput = null;
        }
        imeComposing = false;
        Minecraft.getInstance().onTextInputFocusChange(this, false);
    }

    /**
     * IME 组词更新（原版 EditBox 同款机制）：合成中的文本只展示不写入，
     * 提交的字符走 {@link #charTyped} 正常插入；空事件表示组词结束。
     */
    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        InputNode input = focusedInput;
        if (input == null || !input.focused()) {
            return false;
        }
        if (event == null || event.blocks().isEmpty()) {
            input.clearPreedit();
            imeComposing = false;
        } else {
            input.setPreedit(event.fullText());
            imeComposing = true;
        }
        return true;
    }

    private void submitInput(InputNode input) {
        if (input.action().isEmpty()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("text", input.text());
        int index = input.enclosingItemIndex();
        if (index >= 0) {
            payload.addProperty("index", index);
        }
        ProtocolClient.sendAction(input.action(), payload);
        input.clear(); // 发送后清空，便于连续发言
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        blurInput();
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
        float target = node.hovered() ? 1f : 0f;
        float current = node.hoverProgress();
        if (current != target) {
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
