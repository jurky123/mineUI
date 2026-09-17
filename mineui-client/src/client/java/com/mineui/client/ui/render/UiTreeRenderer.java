package com.mineui.client.ui.render;

import com.mineui.ui.paint.UiColors;
import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.BoxNode;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ContainerNode;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.GenerationSource;
import com.mineui.ui.tree.ImageNode;
import com.mineui.ui.tree.InputNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.ScrollViewNode;
import com.mineui.ui.tree.SliderNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
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

    private final RenderCache cache;

    public UiTreeRenderer(GuiGraphicsExtractor graphics, Font font, StateAccess state) {
        this(graphics, font, state, new RenderCache());
    }

    public UiTreeRenderer(GuiGraphicsExtractor graphics, Font font, StateAccess state, RenderCache cache) {
        this.graphics = graphics;
        this.font = font;
        this.state = state;
        this.cache = cache;
        this.painter = new UiPainter(graphics);
    }

    private int generation() {
        return state instanceof GenerationSource source ? source.generation() : 0;
    }

    /** 解析贴图模板（支持 {state.x} 与 sprite: 前缀），空/非法返回 empty（安全跳过）。 */
    private java.util.Optional<RenderCache.TextureRef> resolveTexture(String template) {
        return cache.texture(generation(), template, raw -> {
            String resolved = Bindings.resolve(raw, state);
            if (resolved.isEmpty()) {
                return java.util.Optional.empty();
            }
            if (resolved.startsWith(SPRITE_PREFIX)) {
                Identifier sprite = Identifier.tryParse(resolved.substring(SPRITE_PREFIX.length()));
                return sprite == null ? java.util.Optional.empty()
                        : java.util.Optional.of(new RenderCache.TextureRef(sprite, true));
            }
            Identifier id = Identifier.tryParse(resolved);
            return id == null ? java.util.Optional.empty()
                    : java.util.Optional.of(new RenderCache.TextureRef(id, false));
        });
    }

    public void render(UiNode root, int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        deferredOverlays.clear();
        renderNode(root, 1f);

        if (!deferredOverlays.isEmpty()) {
            // 覆盖层独立绘制：正常遍历后裁剪栈已归零；
            // 若仍有残留（异常路径），按已 push 的次数成对弹出，避免 Scissor stack underflow
            while (!clips.isEmpty()) {
                clips.pop();
                graphics.disableScissor();
            }
            List<UiNode> overlays = new ArrayList<>(deferredOverlays);
            overlays.sort(Comparator.comparingInt(node -> node.style().z()));
            for (UiNode overlay : overlays) {
                renderNode(overlay, 1f);
            }
        }
    }

    /** 图片节点用 {@code sprite:<namespace>:<path>} 直接引用原版九宫格精灵。 */
    private static final String SPRITE_PREFIX = "sprite:";

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

        float hoverFactor = 1f + (node.style().hoverScale() - 1f) * node.hoverProgress();
        float scale = node.animScale() * hoverFactor;
        boolean transformed = node.hasTransform() || Math.abs(scale - 1f) > 0.001f;
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
            if (Math.abs(scale - 1f) > 0.001f) {
                pose.scale(scale, scale);
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
            case InputNode input -> renderInput(input, opacity);
            case SliderNode slider -> renderSlider(slider, opacity);
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

    // ---------- 表面（背景/描边/阴影/渐变/原版精灵） ----------

    /** 按状态选择背景精灵：聚焦 > 悬停 > 普通。 */
    private String effectiveSprite(UiNode node) {
        NodeStyle style = node.style();
        if (node instanceof InputNode input) {
            if (input.focused() && style.spriteFocus() != null) {
                return style.spriteFocus();
            }
        }
        if (node.hovered() && style.spriteHover() != null) {
            return style.spriteHover();
        }
        return style.sprite();
    }

    private boolean drawSpriteBackground(UiNode node) {
        String sprite = effectiveSprite(node);
        if (sprite == null || sprite.isEmpty()) {
            return false;
        }
        Identifier id = Identifier.tryParse(sprite);
        if (id == null) {
            return false;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.width()), Math.round(node.height()), 0xFFFFFFFF);
        return true;
    }

    private void drawSurface(UiNode node, float opacity) {
        NodeStyle style = node.style();
        if (style.shadowColor() != null && style.shadowSize() > 0f) {
            painter.shadow(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.shadowColor(), style.shadowSize(), style.shadowOffsetY(), opacity);
        }
        if (drawSpriteBackground(node)) {
            return;
        }
        boolean cyclic = hasCycleColors(style);
        if (style.borderColor() != null && style.borderWidth() > 0f) {
            painter.fillRounded(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.borderColor(), opacity);
            if (style.background() != null || cyclic) {
                float inset = style.borderWidth();
                drawFill(node, node.x() + inset, node.y() + inset,
                        Math.max(0f, node.width() - 2f * inset), Math.max(0f, node.height() - 2f * inset),
                        Math.max(0f, style.radius() - inset), opacity);
            }
        } else if (style.background() != null || cyclic) {
            drawFill(node, node.x(), node.y(), node.width(), node.height(), style.radius(), opacity);
        }
    }

    private boolean hasCycleColors(NodeStyle style) {
        return style.cycle() != null && !style.cycle().colors().isEmpty();
    }

    private void drawFill(UiNode node, float x, float y, float w, float h, float radius, float opacity) {
        NodeStyle style = node.style();
        if (hasCycleColors(style)) {
            int color = style.cycle().colorAt(System.currentTimeMillis(),
                    style.background() == null ? 0xFFFFFFFF : style.background());
            painter.fillRounded(x, y, w, h, radius, color, opacity);
            return;
        }
        if (style.gradientTo() != null) {
            painter.fillRoundedGradient(x, y, w, h, radius, style.background(), style.gradientTo(), opacity);
        } else {
            painter.fillRounded(x, y, w, h, radius, style.background(), opacity);
        }
    }

    private void drawButtonBackground(ButtonNode node, float opacity) {
        NodeStyle style = node.style();
        if (style.shadowColor() != null && style.shadowSize() > 0f) {
            painter.shadow(node.x(), node.y(), node.width(), node.height(), style.radius(),
                    style.shadowColor(), style.shadowSize(), style.shadowOffsetY(), opacity);
        }
        if (drawSpriteBackground(node)) {
            return;
        }
        int background = UiColors.lerp(node.background(), node.hoverBackground(), node.hoverProgress());
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
        if (node.style().cycle() != null && !node.style().cycle().colors().isEmpty()) {
            color = node.style().cycle().colorAt(System.currentTimeMillis(), color);
        }
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
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        java.util.Optional<RenderCache.TextureRef> resolved = resolveTexture(node.texture());
        if (resolved.isEmpty()) {
            return;
        }
        RenderCache.TextureRef reference = resolved.get();
        if (reference.sprite()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, reference.id(),
                    Math.round(node.x()), Math.round(node.y()),
                    Math.round(node.width()), Math.round(node.height()), 0xFFFFFFFF);
            return;
        }
        Identifier texture = reference.id();
        float u0 = node.u() / node.textureWidth();
        float v0 = node.v() / node.textureHeight();
        float u1 = (node.u() + node.regionWidth()) / node.textureWidth();
        float v1 = (node.v() + node.regionHeight()) / node.textureHeight();
        graphics.blit(texture,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.x() + node.width()), Math.round(node.y() + node.height()),
                u0, v0, u1, v1);
    }

    // ---------- 文本输入 ----------

    private void renderInput(InputNode node, float opacity) {
        drawSurface(node, opacity);
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        float padX = 6f;
        float innerX = node.x() + padX;
        float innerW = Math.max(0f, node.width() - padX * 2f);
        String content = node.text();
        boolean empty = content.isEmpty();
        String display = empty ? node.placeholder() : content;
        int color = empty ? node.placeholderColor() : node.textColor();
        float prefixW = font.width(content.substring(0, node.cursor()));
        float scroll = node.focused() && prefixW > innerW - 2f ? prefixW - (innerW - 2f) : 0f;
        float drawX = innerX - scroll;
        float drawY = node.y() + (node.height() - font.lineHeight) / 2f;

        pushClipRect(innerX, node.y(), node.x() + node.width(), node.y() + node.height());
        graphics.text(font, display, Math.round(drawX), Math.round(drawY),
                UiColors.withOpacity(color, opacity), true);
        if (node.focused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caretX = drawX + prefixW;
            graphics.fill(Math.round(caretX), Math.round(drawY), Math.round(caretX) + 1,
                    Math.round(drawY + font.lineHeight), UiColors.withOpacity(node.textColor(), opacity));
        }
        popClip();
    }

    // ---------- 3D 预览 ----------

    private void renderItem(ItemViewNode node) {
        String itemTemplate = node.hovered() && node.hoverItem() != null ? node.hoverItem() : node.item();
        if (node.style().cycle() != null && !node.style().cycle().items().isEmpty()) {
            itemTemplate = node.style().cycle().itemAt(System.currentTimeMillis(), itemTemplate);
        }
        int generation = generation();
        String itemId = cache.itemId(generation, itemTemplate, raw -> Bindings.resolve(raw, state));
        if (itemId.isEmpty()) {
            return;
        }
        java.util.List<String> models = node.modelStrings().isEmpty()
                ? java.util.List.of()
                : cache.modelStrings(generation, node.modelStrings(), raw -> Bindings.resolve(raw, state));
        ItemStack stack = ItemStacks.resolve(node, itemId, models);
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
        String value = null;
        String signature = null;
        if (node.hasSkin()) {
            value = Bindings.resolve(node.skinValue(), state);
            signature = node.skinSignature() == null ? null : Bindings.resolve(node.skinSignature(), state);
            if (value == null || value.isEmpty()) {
                return;
            }
            if (signature != null && signature.isBlank()) {
                signature = null;
            }
        }
        AbstractClientPlayer player = EntityPreviews.player(node, value, signature);
        if (player == null) {
            return;
        }
        // 本地滚轮缩放只作用于渲染尺寸，不影响布局
        renderPreview(player, node, (float) (node.scale() * node.zoom()), node.followMouse());
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

    // ---------- 滑块（原版贴图） ----------

    private static final Identifier SLIDER_SPRITE = Identifier.withDefaultNamespace("widget/slider");
    private static final Identifier SLIDER_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("widget/slider_highlighted");
    private static final Identifier SLIDER_HANDLE_SPRITE = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier SLIDER_HANDLE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");

    private void renderSlider(SliderNode node, float opacity) {
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        boolean active = node.dragging() || node.hovered();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                active ? SLIDER_HIGHLIGHTED_SPRITE : SLIDER_SPRITE,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.width()), Math.round(node.height()), 0xFFFFFFFF);

        float handleWidth = 8f;
        float ratio = (float) Math.max(0.0, Math.min(1.0, node.ratio(state)));
        float handleX = node.x() + ratio * (node.width() - handleWidth);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                active ? SLIDER_HANDLE_HIGHLIGHTED_SPRITE : SLIDER_HANDLE_SPRITE,
                Math.round(handleX), Math.round(node.y()),
                Math.round(handleWidth), Math.round(node.height()), 0xFFFFFFFF);

        String label = node.label(state);
        if (!label.isEmpty()) {
            int textWidth = font.width(label);
            graphics.text(font, label,
                    Math.round(node.x() + (node.width() - textWidth) / 2f),
                    Math.round(node.y() + (node.height() - font.lineHeight) / 2f),
                    UiColors.withOpacity(node.textColor(), opacity), true);
        }
    }

    // ---------- 滑动条（滚动条） ----------

    private static final Identifier SCROLLER_TRACK_SPRITE = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final Identifier SCROLLER_THUMB_SPRITE = Identifier.withDefaultNamespace("widget/scroller");

    private void drawScrollbar(ScrollViewNode node, float opacity) {
        if (!node.scrollable()) {
            return;
        }
        float barWidth = 6f;
        float x = node.x() + node.width() - barWidth;
        float y = node.y();
        float trackHeight = node.height();
        if (trackHeight <= 8f) {
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_TRACK_SPRITE,
                Math.round(x), Math.round(y), Math.round(barWidth), Math.round(trackHeight), 0xFFFFFFFF);

        float ratio = node.height() / node.contentHeight();
        float thumbHeight = Math.max(8f, trackHeight * ratio);
        float maxScroll = node.contentHeight() - node.height();
        float t = maxScroll <= 0f ? 0f : node.scrollOffset() / maxScroll;
        float thumbY = y + (trackHeight - thumbHeight) * t;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_THUMB_SPRITE,
                Math.round(x), Math.round(thumbY), Math.round(barWidth), Math.round(thumbHeight), 0xFFFFFFFF);
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

    private void pushClipRect(float x1, float y1, float x2, float y2) {
        float[] rect = {x1, y1, x2, y2};
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
        // enableScissor 会入栈，disableScissor 出栈一次：必须严格 1:1
        clips.pop();
        graphics.disableScissor();
    }
}
