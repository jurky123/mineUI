package com.mineui.client.ui.render;

import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.BoxNode;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ContainerNode;
import com.mineui.ui.tree.ImageNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** 把 UI 节点树画到屏幕上（服务端状态 + 本地定义）。 */
public final class UiTreeRenderer {

    private UiTreeRenderer() {
    }

    public static void render(UiNode node, GuiGraphicsExtractor graphics, Font font, StateAccess state) {
        switch (node) {
            case ContainerNode container -> {
                renderBackground(container, graphics);
                for (UiNode child : container.children()) {
                    render(child, graphics, font, state);
                }
            }
            case TextNode text -> renderText(text, graphics, font, state);
            case ButtonNode button -> renderButton(button, graphics, font, state);
            case ImageNode image -> renderImage(image, graphics);
            case BoxNode box -> renderBackground(box, graphics);
            default -> {
            }
        }
    }

    private static void renderBackground(UiNode node, GuiGraphicsExtractor graphics) {
        Integer background = node.style().background();
        if (background == null) {
            return;
        }
        graphics.fill((int) node.x(), (int) node.y(),
                (int) (node.x() + node.width()), (int) (node.y() + node.height()), background);
    }

    private static void renderText(TextNode node, GuiGraphicsExtractor graphics, Font font, StateAccess state) {
        String text = Bindings.resolve(node.template(), state);
        if (text.isEmpty()) {
            return;
        }
        float scale = node.scale();
        float textWidth = font.width(text) * scale;
        float drawX = switch (node.style().align()) {
            case CENTER -> node.x() + (node.width() - textWidth) / 2f;
            case END -> node.x() + node.width() - textWidth;
            default -> node.x();
        };
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(drawX, node.y());
        pose.scale(scale, scale);
        graphics.text(font, text, 0, 0, node.color(), true);
        pose.popMatrix();
    }

    private static void renderButton(ButtonNode node, GuiGraphicsExtractor graphics, Font font, StateAccess state) {
        int background = node.hovered() ? node.hoverBackground() : node.background();
        graphics.fill((int) node.x(), (int) node.y(),
                (int) (node.x() + node.width()), (int) (node.y() + node.height()), background);

        String text = Bindings.resolve(node.template(), state);
        if (text.isEmpty()) {
            return;
        }
        int textWidth = font.width(text);
        graphics.text(font, text,
                (int) (node.x() + (node.width() - textWidth) / 2f),
                (int) (node.y() + (node.height() - font.lineHeight) / 2f),
                node.textColor(), true);
    }

    private static void renderImage(ImageNode node, GuiGraphicsExtractor graphics) {
        Identifier texture = Identifier.tryParse(node.texture());
        if (texture == null) {
            return;
        }
        float u0 = node.u() / node.textureWidth();
        float v0 = node.v() / node.textureHeight();
        float u1 = (node.u() + node.regionWidth()) / node.textureWidth();
        float v1 = (node.v() + node.regionHeight()) / node.textureHeight();
        graphics.blit(texture,
                (int) node.x(), (int) node.y(),
                (int) (node.x() + node.width()), (int) (node.y() + node.height()),
                u0, v0, u1, v1);
    }
}
