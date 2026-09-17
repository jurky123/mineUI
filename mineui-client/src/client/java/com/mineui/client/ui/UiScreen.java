package com.mineui.client.ui;

import com.mineui.client.net.ProtocolClient;
import com.mineui.client.ui.render.UiTreeRenderer;
import com.mineui.ui.spec.UiDefinition;
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

/**
 * 通用 MineUI 界面：节点树来自客户端 UI JSON，数据来自服务端 state。
 * F9 热重载（开发目录覆盖）。
 */
public final class UiScreen extends Screen {

    private static final int BACKGROUND = 0xC0101010;

    private final String app;
    private final String view;
    private final String source;
    private final UiNode root;
    private final TextMeasurer measurer;

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
        graphics.text(this.font, source, 6, 6, 0xFF606060, false);
        UiTreeRenderer.render(root, graphics, this.font, ProtocolClient.state());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        UiNode hit = root.mouseClicked(event.x(), event.y(), event.button());
        if (hit instanceof ButtonNode button) {
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
        super.mouseMoved(mouseX, mouseY);
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
}
