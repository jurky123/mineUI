package com.mineui.client.ui.render;

import com.mineui.ui.paint.UiColors;
import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.BoxNode;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ContainerNode;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.ImageNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.ScrollViewNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 把 UI 节点树画到屏幕上。
 * 支持：圆角/描边/渐变/阴影、透明度继承、裁剪栈、z 序、变换（位移/缩放/旋转）。
 */
public final class UiTreeRenderer {

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final StateAccess state;
    private final UiPainter painter;
    private final Deque<float[]> clips = new ArrayDeque<>();
    /** 延迟到整棵树画完后再画的模态节点（避免被内容容器的裁剪波及）。 */
    private final List<UiNode> deferredOverlays = new ArrayList<>();

    private int mouseX;
    private int mouseY;

    public UiTreeRenderer(GuiGraphicsExtractor graphics, Font font, StateAccess state) {
        this.graphics = graphics;
        this.font = font;
        this.state = state;
        this.painter = new UiPainter(graphics);
    }

    public void render(UiNode root, int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        deferredOverlays.clear();
        renderNode(root, 1f);

        if (!deferredOverlays.isEmpty()) {
            // 覆盖层独立绘制：清空裁剪栈，确保模态永远在最上层、不被内容区裁剪
            clips.clear();
            graphics.disableScissor();
            List<UiNode> overlays = new ArrayList<>(deferredOverlays);
            overlays.sort(Comparator.comparingInt(node -> node.style().z()));
            for (UiNode overlay : overlays) {
                renderNode(overlay, 1f);
            }
        }
    }

    private void renderNode(UiNode node, float inheritedOpacity) {
        if (!node.visibleNow()) {
            return;
        }
        float opacity = inheritedOpacity * node.animOpacity();
        boolean clipped = false;
        if ((node instanceof ScrollViewNode || node.style().clip()) && node.width() > 0 && node.height() > 0) {
            pushClip(node);
            clipped = true;
        }

        boolean transformed = node.hasTransform();
        var pose = graphics.pose();
        if (transformed) {
            float centerX = node.x() + node.width() / 2f;
            float centerY = node.y() + node.height() / 2f;
            pose.pushMatrix();
            pose.translate(node.animOffsetX(), node.animOffsetY());
            pose.translate(centerX, centerY);
            if (node.animRotation() != 0f) {
                pose.rotate((float) Math.toRadians(node.animRotation()));
            }
            if (node.animScale() != 1f) {
                pose.scale(node.animScale(), node.animScale());
            }
            pose.translate(-centerX, -centerY);
        }

        switch (node) {
            case ContainerNode container -> {
                drawSurface(container, opacity);
                List<UiNode> children = new ArrayList<>(container.children());
                children.sort(Comparator.comparingInt(child -> child.style().z()));
                for (UiNode child : children) {
                    if (child.style().modal()) {
                        deferredOverlays.add(child);
                        continue;
                    }
                    renderNode(child, opacity);
                }
                if (container instanceof ScrollViewNode scrollView) {
                    drawScrollbar(scrollView, opacity);
                }
            }
            case TextNode text -> renderText(text, opacity);
            case ButtonNode button -> {
                drawButtonBackground(button, opacity);
                renderText(button, opacity);
            }
            case ImageNode image -> renderImage(image);
            case ItemViewNode item -> renderItem(item);
            case EntityViewNode entityView -> renderEntityPreview(entityView);
            case PlayerViewNode playerView -> renderPlayerPreview(playerView);
            case BoxNode box -> drawSurface(box, opacity);
            default -> {
            }
        }

        if (transformed) {
            pose.popMatrix();
        }
        if (clipped) {
            popClip();
        }
    }

    // ---------- 表面（背景/描边/阴影/渐变） ----------

    private void drawSurface(UiNode node, float opacity) {
        NodeStyle style = node.style();
        if (style.shadowColor() != null && style.shadowSize() > 0f) {
            painter.shadow(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.shadowColor(), style.shadowSize(), style.shadowOffsetY(), opacity);
        }
        if (style.borderColor() != null && style.borderWidth() > 0f) {
            painter.fillRounded(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.borderColor(), opacity);
            if (style.background() != null) {
                float inset = style.borderWidth();
                drawFill(node, node.x() + inset, node.y() + inset,
                        Math.max(0f, node.width() - 2f * inset), Math.max(0f, node.height() - 2f * inset),
                        Math.max(0f, style.radius() - inset), opacity);
            }
        } else if (style.background() != null) {
            drawFill(node, node.x(), node.y(), node.width(), node.height(), style.radius(), opacity);
        }
    }

    private void drawFill(UiNode node, float x, float y, float w, float h, float radius, float opacity) {
        NodeStyle style = node.style();
        if (style.gradientTo() != null) {
            painter.fillRoundedGradient(x, y, w, h, radius, style.background(), style.gradientTo(), opacity);
        } else {
            painter.fillRounded(x, y, w, h, radius, style.background(), opacity);
        }
    }

    private void drawButtonBackground(ButtonNode node, float opacity) {
        int background = UiColors.lerp(node.background(), node.hoverBackground(), node.hoverProgress());
        NodeStyle style = node.style();
        if (style.shadowColor() != null && style.shadowSize() > 0f) {
            painter.shadow(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.shadowColor(), style.shadowSize(), style.shadowOffsetY(), opacity);
        }
        painter.fillRounded(node.x(), node.y(), node.width(), node.height(), style.radius(), background, opacity);
    }

    // ---------- 内容 ----------

    private void renderText(UiNode node, float opacity) {
        String template = switch (node) {
            case TextNode text -> text.template();
            case ButtonNode button -> button.template();
            default -> "";
        };
        String text = Bindings.resolve(template, state);
        if (text.isEmpty()) {
            return;
        }
        float scale = node instanceof TextNode textNode ? textNode.scale() : 1f;
        int color = node instanceof TextNode textNode ? textNode.color() : ((ButtonNode) node).textColor();
        int argb = UiColors.withOpacity(color, opacity);
        if (((argb >>> 24) & 0xFF) == 0) {
            return;
        }

        float textWidth = font.width(text) * scale;
        float drawX;
        if (node instanceof ButtonNode) {
            drawX = node.x() + (node.width() - textWidth) / 2f;
        } else {
            drawX = switch (node.style().align()) {
                case CENTER -> node.x() + (node.width() - textWidth) / 2f;
                case END -> node.x() + node.width() - textWidth;
                default -> node.x();
            };
        }
        float drawY = node.y() + (node.height() - font.lineHeight * scale) / 2f;

        var pose = graphics.pose();
        boolean scaled = scale != 1f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(drawX, drawY);
            pose.scale(scale, scale);
        }
        graphics.text(font, text, scaled ? 0 : Math.round(drawX), scaled ? 0 : Math.round(drawY), argb, true);
        if (scaled) {
            pose.popMatrix();
        }
    }

    private void renderImage(ImageNode node) {
        Identifier texture = Identifier.tryParse(node.texture());
        if (texture == null || node.width() <= 0 || node.height() <= 0) {
            return;
        }
        float u0 = node.u() / node.textureWidth();
        float v0 = node.v() / node.textureHeight();
        float u1 = (node.u() + node.regionWidth()) / node.textureWidth();
        float v1 = (node.v() + node.regionHeight()) / node.textureHeight();
        graphics.blit(texture,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.x() + node.width()), Math.round(node.y() + node.height()),
                u0, v0, u1, v1);
    }

    // ---------- 3D 预览 ----------

    private void renderItem(ItemViewNode node) {
        ItemStack stack = ItemStacks.resolve(node);
        if (stack.isEmpty()) {
            return;
        }
        float scale = node.scale();
        var pose = graphics.pose();
        boolean scaled = scale != 1f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(node.x(), node.y());
            pose.scale(scale, scale);
        }
        int x = scaled ? 0 : Math.round(node.x());
        int y = scaled ? 0 : Math.round(node.y());
        if (node.fake()) {
            graphics.fakeItem(stack, x, y);
        } else {
            graphics.item(stack, x, y);
            graphics.itemDecorations(font, stack, x, y);
        }
        if (scaled) {
            pose.popMatrix();
        }
    }

    private void renderEntityPreview(EntityViewNode node) {
        LivingEntity entity = EntityPreviews.entity(node);
        if (entity == null) {
            return;
        }
        renderPreview(entity, node, node.scale(), node.followMouse());
    }

    private void renderPlayerPreview(PlayerViewNode node) {
        AbstractClientPlayer player = EntityPreviews.player(node);
        if (player == null) {
            return;
        }
        renderPreview(player, node, node.scale(), node.followMouse());
    }

    private void renderPreview(LivingEntity entity, UiNode node, float scale, boolean followMouse) {
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        float centerX = node.x() + node.width() / 2f;
        float centerY = node.y() + node.height() / 2f;
        float lookX = followMouse ? mouseX : centerX;
        float lookY = followMouse ? mouseY : centerY;
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.x() + node.width()), Math.round(node.y() + node.height()),
                Math.max(1, Math.round(scale)), 0.0625f, lookX, lookY, entity);
    }

    // ---------- 滚动条 ----------

    private void drawScrollbar(ScrollViewNode node, float opacity) {
        if (!node.scrollable()) {
            return;
        }
        float trackWidth = 3f;
        float x = node.x() + node.width() - trackWidth - 2f;
        float y = node.y() + 2f;
        float trackHeight = node.height() - 4f;
        if (trackHeight <= 6f) {
            return;
        }
        painter.fillRounded(x, y, trackWidth, trackHeight, trackWidth / 2f, 0x50000000, opacity);

        float ratio = node.height() / node.contentHeight();
        float thumbHeight = Math.max(10f, trackHeight * ratio);
        float maxScroll = node.contentHeight() - node.height();
        float t = maxScroll <= 0f ? 0f : node.scrollOffset() / maxScroll;
        float thumbY = y + (trackHeight - thumbHeight) * t;
        painter.fillRounded(x, thumbY, trackWidth, thumbHeight, trackWidth / 2f, 0xC0A8C8E8, opacity);
    }

    // ---------- 裁剪栈 ----------

    private void pushClip(UiNode node) {
        float[] rect = {
                node.x() + node.animOffsetX(),
                node.y() + node.animOffsetY(),
                node.x() + node.width() + node.animOffsetX(),
                node.y() + node.height() + node.animOffsetY()
        };
        float[] current = clips.peek();
        if (current != null) {
            rect[0] = Math.max(rect[0], current[0]);
            rect[1] = Math.max(rect[1], current[1]);
            rect[2] = Math.min(rect[2], current[2]);
            rect[3] = Math.min(rect[3], current[3]);
        }
        clips.push(rect);
        graphics.enableScissor((int) rect[0], (int) rect[1], (int) rect[2], (int) rect[3]);
    }

    private void popClip() {
        clips.pop();
        float[] current = clips.peek();
        if (current != null) {
            graphics.enableScissor((int) current[0], (int) current[1], (int) current[2], (int) current[3]);
        } else {
            graphics.disableScissor();
        }
    }
}
