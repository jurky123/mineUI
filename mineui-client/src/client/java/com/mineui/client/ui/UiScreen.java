package com.mineui.client.ui;

import com.mineui.client.net.ProtocolClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Phase 1 测试界面：黑底 + 服务端下发的标题/计数 + 一个按钮。
 * 服务端只发 state，渲染完全在客户端。
 */
public final class UiScreen extends Screen {

    private static final int BACKGROUND = 0xE0101010;

    private final String app;
    private final String view;

    public UiScreen(String app, String view) {
        super(Component.literal("MineUI " + app + "/" + view));
        this.app = app;
        this.view = view;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("点我 +1"), button -> ProtocolClient.sendAction("button_click"))
                .bounds(this.width / 2 - 60, this.height / 2 + 10, 120, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiStateStore state = ProtocolClient.state();
        graphics.text(this.font, app + "/" + view, 8, 8, 0xFF707070);
        graphics.centeredText(this.font, Component.literal(state.getString("title", "MineUI")),
                this.width / 2, 40, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("count = " + state.getInt("count", 0)),
                this.width / 2, 70, 0xFF00E0FF);
        graphics.centeredText(this.font, Component.literal("Esc 关闭 · /mineui test 重开"),
                this.width / 2, this.height - 30, 0xFF909090);
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
