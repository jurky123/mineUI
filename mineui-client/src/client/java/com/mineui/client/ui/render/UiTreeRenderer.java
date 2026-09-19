package com.mineui.client.ui.render;

import com.mineui.ui.paint.UiColors;
import com.mineui.ui.tree.Bindings;
import com.mineui.ui.tree.BoxNode;
import com.mineui.ui.tree.CheckboxNode;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ContainerNode;
import com.mineui.ui.tree.EntityViewNode;
import com.mineui.ui.tree.GenerationSource;
import com.mineui.ui.tree.ImageNode;
import com.mineui.ui.tree.InputNode;
import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.ListViewNode;
import com.mineui.client.MineUiTheme;
import com.mineui.client.ui.remote.RemoteImages;
import com.mineui.ui.tree.NodeStyle;
import com.mineui.ui.tree.PlayerViewNode;
import com.mineui.ui.tree.ProgressNode;
import com.mineui.ui.tree.ScrollViewNode;
import com.mineui.ui.tree.SliderNode;
import com.mineui.ui.tree.SwitchNode;
import com.mineui.ui.tree.TabsNode;
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
    /** 当前状态上下文；列表条目渲染时临时切换为条目状态。 */
    private StateAccess state;
    private final UiPainter painter;
    private final Deque<float[]> clips = new ArrayDeque<>();
    /** 延迟到整棵树画完后再画的模态节点（避免被内容容器的裁剪波及）。 */
    private final List<UiNode> deferredOverlays = new ArrayList<>();

    /** 列表高亮行的文字颜色覆盖（仅对高亮条目内的 TextNode 生效）。 */
    private Integer highlightTextColor;

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
        StateAccess previousState = state;
        Integer previousHighlight = highlightTextColor;
        if (node.stateContext() != null) {
            state = node.stateContext();
        }
        try {
            renderNodeInner(node, inheritedOpacity);
        } finally {
            state = previousState;
            highlightTextColor = previousHighlight;
        }
    }

    private void renderNodeInner(UiNode node, float inheritedOpacity) {
        if (!node.visibleNow()) {
            return;
        }
        float opacity = inheritedOpacity * node.animOpacity();
        boolean clipped = false;
        if ((node instanceof ScrollViewNode || node instanceof ListViewNode || node.style().clip())
                && node.width() > 0 && node.height() > 0) {
            pushClip(node);
            clipped = true;
        }

        float hoverFactor = 1f + (node.style().hoverScale() - 1f) * node.hoverProgress();
        float scale = node.animScale() * hoverFactor;
        // 持续旋转（本地时钟）叠加动画旋转；占位帧（遮罩烘焙中/图片加载中）不推进时钟，成圆后从 0° 起转
        float spin = node.spinAngle(System.currentTimeMillis(), state, contentReady(node));
        float rotation = node.animRotation() + spin;
        boolean transformed = node.hasTransform() || Math.abs(scale - 1f) > 0.001f || rotation != 0f;
        var pose = graphics.pose();
        if (transformed) {
            float centerX = node.x() + node.width() / 2f;
            float centerY = node.y() + node.height() / 2f;
            pose.pushMatrix();
            pose.translate(node.animOffsetX(), node.animOffsetY());
            pose.translate(centerX, centerY);
            if (rotation != 0f) {
                pose.rotate((float) Math.toRadians(rotation));
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
                UiNode highlighted = container instanceof ListViewNode list ? list.highlightedItem() : null;
                Integer highlightColor = container instanceof ListViewNode list ? list.highlightColor() : null;
                for (UiNode child : children) {
                    if (child.style().modal()) {
                        deferredOverlays.add(child);
                        continue;
                    }
                    boolean highlightThis = highlighted != null && child == highlighted && highlightColor != null;
                    if (highlightThis) {
                        highlightTextColor = highlightColor;
                    }
                    renderNode(child, opacity);
                    if (highlightThis) {
                        highlightTextColor = null;
                    }
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
            case ImageNode image -> renderImage(image, opacity);
            case ItemViewNode item -> renderItem(item);
            case EntityViewNode entityView -> renderEntityPreview(entityView);
            case PlayerViewNode playerView -> renderPlayerPreview(playerView);
            case InputNode input -> renderInput(input, opacity);
            case SliderNode slider -> renderSlider(slider, opacity);
            case ProgressNode progress -> renderProgress(progress, opacity);
            case CheckboxNode checkbox -> renderCheckbox(checkbox);
            case SwitchNode switchNode -> renderSwitch(switchNode);
            case TabsNode tabs -> renderTabs(tabs);
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
        return drawSpriteBackground(node, 0);
    }

    /**
     * 背景精灵：支持原版九宫格精灵，以及程序化皮肤（bevel/inset/grid/accent）
     * 与任意纹理 9-slice（{@code sprite9:<id>#<边距>}）。返回 true 表示已绘制。
     */
    private boolean drawSpriteBackground(UiNode node, int dy) {
        String sprite = effectiveSprite(node);
        if (sprite == null || sprite.isEmpty()) {
            return false;
        }
        switch (sprite) {
            case "mineui:bevel" -> {
                drawBevel(node, dy, node instanceof ButtonNode button && button.pressed());
                return true;
            }
            case "mineui:inset" -> {
                drawInset(node);
                return true;
            }
            case "mineui:grid" -> {
                drawGrid(node);
                return true;
            }
            case "mineui:accent" -> {
                painter.fillRounded(node.x(), node.y() + dy, node.width(), node.height(),
                        node.style().radius(), MineUiTheme.accent(), 1f);
                return true;
            }
            default -> {
                if (sprite.startsWith("sprite9:")) {
                    return drawSprite9(node, sprite, dy);
                }
            }
        }
        Identifier id = Identifier.tryParse(sprite);
        if (id == null) {
            return false;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id,
                Math.round(node.x()), Math.round(node.y() + dy),
                Math.round(node.width()), Math.round(node.height()), 0xFFFFFFFF);
        return true;
    }

    /** 原版式 1px bevel：外描边 + 左上高光 + 右下暗边（pressed 反转 = 凹陷）。 */
    private void drawBevel(UiNode node, int dy, boolean inverted) {
        NodeStyle style = node.style();
        int face = style.background() == null ? 0xFFC6C6C6 : style.background();
        int highlight = UiColors.lerp(face, 0xFFFFFFFF, 0.45f);
        int shadow = UiColors.lerp(face, 0xFF000000, 0.45f);
        int outline = UiColors.lerp(face, 0xFF000000, 0.75f);
        int x = Math.round(node.x());
        int y = Math.round(node.y()) + dy;
        int w = Math.round(node.width());
        int h = Math.round(node.height());
        int topLight = inverted ? shadow : highlight;
        int leftLight = inverted ? shadow : highlight;
        int bottomDark = inverted ? highlight : shadow;
        int rightDark = inverted ? highlight : shadow;
        if (style.radius() <= 1f) {
            // 直角：标准原版结构（1px 外描边 + 左上高光 / 右下暗边 + 平面）
            painter.fillRounded(x, y, w, h, 0f, face, 1f);
            graphics.fill(x, y, x + w, y + 1, topLight);
            graphics.fill(x, y, x + 1, y + h, leftLight);
            graphics.fill(x, y + h - 1, x + w, y + h, bottomDark);
            graphics.fill(x + w - 1, y, x + w, y + h, rightDark);
            graphics.fill(x - 1, y - 1, x + w + 1, y, outline);
            graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, outline);
            graphics.fill(x - 1, y, x, y + h, outline);
            graphics.fill(x + w, y, x + w + 1, y + h, outline);
        } else {
            // 圆角：面 + 顶/底 1px 高光/暗边近似
            painter.fillRounded(x, y, w, h, style.radius(), face, 1f);
            graphics.fill(x, y, x + w, y + 1, topLight);
            graphics.fill(x, y + h - 1, x + w, y + h, bottomDark);
        }
    }

    /** 凹陷内槽（原版物品槽风格）：面向下 + 上左暗、右下亮。 */
    private void drawInset(UiNode node) {
        NodeStyle style = node.style();
        int face = style.background() == null ? 0xFF8B8B8B : style.background();
        int x = Math.round(node.x());
        int y = Math.round(node.y());
        int w = Math.round(node.width());
        int h = Math.round(node.height());
        int faceDark = UiColors.lerp(face, 0xFF000000, 0.55f);
        int faceLight = UiColors.lerp(face, 0xFFFFFFFF, 0.35f);
        painter.fillRounded(x, y, w, h, Math.max(0f, style.radius()), face, 1f);
        graphics.fill(x, y, x + w, y + 1, faceDark);
        graphics.fill(x, y, x + 1, y + h, faceDark);
        graphics.fill(x, y + h - 1, x + w, y + h, faceLight);
        graphics.fill(x + w - 1, y, x + w, y + h, faceLight);
    }

    /** 低对比网格背景（8px 网格线）。 */
    private void drawGrid(UiNode node) {
        NodeStyle style = node.style();
        int face = style.background() == null ? 0xFF0E141B : style.background();
        int x = Math.round(node.x());
        int y = Math.round(node.y());
        int w = Math.round(node.width());
        int h = Math.round(node.height());
        painter.fillRounded(x, y, w, h, Math.max(0f, style.radius()), face, 1f);
        for (int gx = x; gx <= x + w; gx += 8) {
            graphics.fill(gx, y, gx + 1, y + h, UiColors.withOpacity(0x14FFFFFF, 1f));
        }
        for (int gy = y; gy <= y + h; gy += 8) {
            graphics.fill(x, gy, x + w, gy + 1, UiColors.withOpacity(0x14FFFFFF, 1f));
        }
    }

    /**
     * 任意纹理 9-slice：{@code sprite9:<贴图id>#<边距>}。
     * 边距为单个数字（四边同值）或 {@code l,t,r,b}；角 1:1、边单向拉伸、中心双向拉伸。
     */
    private boolean drawSprite9(UiNode node, String sprite, int dy) {
        int hash = sprite.indexOf('#');
        if (hash <= "sprite9:".length()) {
            return false;
        }
        Identifier id = Identifier.tryParse(sprite.substring("sprite9:".length(), hash));
        if (id == null) {
            return false;
        }
        int[] insets = parseInsets(sprite.substring(hash + 1));
        if (insets == null) {
            return false;
        }
        int l = insets[0], t = insets[1], r = insets[2], b = insets[3];
        float x = node.x();
        float y = node.y() + dy;
        float w = node.width();
        float h = node.height();
        if (w < l + r + 1f || h < t + b + 1f) {
            return false;
        }
        int[] dims = TextureSizes.resolve(id);
        if (dims[0] <= 0 || dims[1] <= 0) {
            return false;
        }
        float uA = (float) l / dims[0];
        float uB = (float) (dims[0] - r) / dims[0];
        float vA = (float) t / dims[1];
        float vB = (float) (dims[1] - b) / dims[1];
        float xm1 = x + l, xm2 = x + w - r;
        float ym1 = y + t, ym2 = y + h - b;
        // 角（1:1）
        blitRegion(id, x, y, xm1, ym1, 0f, uA, 0f, vA);
        blitRegion(id, xm2, y, x + w, ym1, uB, 1f, 0f, vA);
        blitRegion(id, x, ym2, xm1, y + h, 0f, uA, vB, 1f);
        blitRegion(id, xm2, ym2, x + w, y + h, uB, 1f, vB, 1f);
        // 边（单向拉伸）
        blitRegion(id, xm1, y, xm2, ym1, uA, uB, 0f, vA);
        blitRegion(id, xm1, ym2, xm2, y + h, uA, uB, vB, 1f);
        blitRegion(id, x, ym1, xm1, ym2, 0f, uA, vA, vB);
        blitRegion(id, xm2, ym1, x + w, ym2, uB, 1f, vA, vB);
        // 中心（双向拉伸）
        blitRegion(id, xm1, ym1, xm2, ym2, uA, uB, vA, vB);
        return true;
    }

    private void blitRegion(Identifier id, float x0, float y0, float x1, float y1,
                            float u0, float u1, float v0, float v1) {
        graphics.blit(id, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), u0, u1, v0, v1);
    }

    private static int[] parseInsets(String spec) {
        try {
            String[] parts = spec.split(",");
            if (parts.length == 1) {
                int value = Integer.parseInt(parts[0].trim());
                return new int[]{value, value, value, value};
            }
            if (parts.length == 4) {
                return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim())};
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
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
        int pressedDy = node.pressed() ? 1 : 0;
        if (drawSpriteBackground(node, pressedDy)) {
            return;
        }
        int background = UiColors.lerp(node.background(), node.hoverBackground(), node.hoverProgress());
        if (node.pressed()) {
            // 按下：加深 + 下沉 1px（原版“反转 bevel”的近似）
            background = UiColors.lerp(background, 0xFF0A0A0A, 0.35f);
        }
        painter.fillRounded(node.x(), node.y() + pressedDy, node.width(), node.height(), style.radius(), background, opacity);
    }

    // ---------- 内容 ----------

    private static final Identifier CHECKBOX_SPRITE = Identifier.withDefaultNamespace("widget/checkbox");
    private static final Identifier CHECKBOX_SELECTED_SPRITE = Identifier.withDefaultNamespace("widget/checkbox_selected");

    private void renderCheckbox(CheckboxNode node) {
        boolean hovered = node.hovered();
        Identifier sprite = node.checkedNow()
                ? (hovered ? Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted")
                        : CHECKBOX_SELECTED_SPRITE)
                : (hovered ? Identifier.withDefaultNamespace("widget/checkbox_highlighted")
                        : CHECKBOX_SPRITE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.width()), Math.round(node.height()), 0xFFFFFFFF);
    }

    private void renderSwitch(SwitchNode node) {
        int x = Math.round(node.x());
        int y = Math.round(node.y());
        int w = Math.round(node.width());
        int h = Math.round(node.height());
        int track = node.checkedNow() ? MineUiTheme.accent() : 0xFF565656;
        int knob = node.checkedNow() ? 0xFFFFFFFF : 0xFFC6C6C6;
        painter.fillRounded(x, y, w, h, h / 2f,
                node.hovered() ? UiColors.lerp(track, 0xFFFFFFFF, 0.15f) : track, 1f);
        int knobSize = Math.max(4, h - 4);
        int knobX = node.checkedNow() ? x + w - knobSize - 2 : x + 2;
        painter.fillRounded(knobX, y + 2, knobSize, knobSize, knobSize / 2f, knob, 1f);
    }

    private void renderTabs(TabsNode node) {
        float cursor = node.x();
        for (int i = 0; i < node.items().size(); i++) {
            float itemWidth = node.itemWidth(i);
            if (itemWidth <= 0) {
                continue;
            }
            boolean selected = i == node.selectedIndex();
            boolean hovered = node.hovered()
                    && cursor <= mouseX && mouseX < cursor + itemWidth
                    && node.y() <= mouseY && mouseY < node.y() + node.height();
            Identifier spriteId = Identifier.tryParse(selected
                    ? (hovered ? "minecraft:widget/tab_selected_highlighted" : "minecraft:widget/tab_selected")
                    : (hovered ? "minecraft:widget/tab_highlighted" : "minecraft:widget/tab"));
            if (spriteId != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId,
                        Math.round(cursor), Math.round(node.y()),
                        Math.round(itemWidth), Math.round(node.height()), 0xFFFFFFFF);
            }
            String label = Bindings.resolve(node.items().get(i), state);
            int textWidth = font.width(label);
            int argb = UiColors.withOpacity(selected ? 0xFFFFFFFF : 0xFFA0A0A0, 1f);
            graphics.text(font, label,
                    Math.round(cursor + (itemWidth - textWidth) / 2f),
                    Math.round(node.y() + (node.height() - font.lineHeight) / 2f), argb, true);
            cursor += itemWidth;
        }
    }

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
        // 省略：显式宽度 + ellipsis 时超出自动截断
        if (node instanceof TextNode textNode && textNode.ellipsis() && node.width() > 0 && scale > 0) {
            float maxWidth = node.width() / scale;
            text = com.mineui.ui.util.TextFit.ellipsize(text, maxWidth, font::width);
        }
        int color;
        if (node instanceof TextNode textNode) {
            color = highlightTextColor != null ? highlightTextColor : textNode.color();
        } else {
            color = ((ButtonNode) node).textColor();
        }
        if (node instanceof TextNode textNode && textNode.outlineColor() != 0) {
            renderOutlined(node, text, textNode.outlineColor(), color, opacity, scale);
            return;
        }
        if (node.style().cycle() != null && !node.style().cycle().colors().isEmpty()) {
            color = node.style().cycle().colorAt(System.currentTimeMillis(), color);
        }
        int argb = UiColors.withOpacity(color, opacity);
        if (((argb >>> 24) & 0xFF) == 0) {
            return;
        }

        // 按下中的按钮内容下沉 1px（含文字）
        float dy = node instanceof ButtonNode button && button.pressed() ? 1f : 0f;
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
        float drawY = node.y() + dy + (node.height() - font.lineHeight * scale) / 2f;

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

    /**
     * 带 1px 描边的文本：四方向偏移描边色 + 本体。用于亮/杂背景上的关键文字。
     */
    private void renderOutlined(UiNode node, String text, int outlineColor, int color,
                                float opacity, float scale) {
        int outlineArgb = UiColors.withOpacity(outlineColor, opacity);
        int argb = UiColors.withOpacity(color, opacity);
        if (((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        float textWidth = font.width(text) * scale;
        float drawX;
        drawX = switch (node.style().align()) {
            case CENTER -> node.x() + (node.width() - textWidth) / 2f;
            case END -> node.x() + node.width() - textWidth;
            default -> node.x();
        };
        float drawY = node.y() + (node.height() - font.lineHeight * scale) / 2f;
        var pose = graphics.pose();
        boolean scaled = scale != 1f;
        if (scaled) {
            pose.pushMatrix();
            pose.translate(drawX, drawY);
            pose.scale(scale, scale);
        }
        int x = scaled ? 0 : Math.round(drawX);
        int y = scaled ? 0 : Math.round(drawY);
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            graphics.text(font, text, x + offset[0], y + offset[1], outlineArgb, true);
        }
        graphics.text(font, text, x, y, argb, true);
        if (scaled) {
            pose.popMatrix();
        }
    }

    /** 本帧是否绘制真实内容：遮罩烘焙中 / 图片加载中的占位帧返回 false（不推进旋转时钟）。 */
    private boolean contentReady(UiNode node) {
        if (!(node instanceof ImageNode image)) {
            return true;
        }
        if (image.width() <= 0 || image.height() <= 0) {
            return true;
        }
        float radius = image.style().radius();
        String resolvedUrl = cache.resolvedTemplate(generation(), image.texture(),
                template -> Bindings.resolve(template, state));
        if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
            RemoteImages.Entry entry = RemoteImages.resolve(resolvedUrl, image.sha256());
            if (entry.state() != RemoteImages.State.READY || entry.texture() == null) {
                return false;
            }
            if (radius <= 0.5f) {
                return true;
            }
            // READY/FAILED 视为可绘制（FAILED 回退原图），烘焙中为占位
            return ImageMasks.remote(resolvedUrl, image.width(), image.height(), radius)
                    .state() != ImageMasks.State.LOADING;
        }
        java.util.Optional<RenderCache.TextureRef> reference = resolveTexture(image.texture());
        if (reference.isEmpty()) {
            return false;
        }
        if (reference.get().sprite() || radius <= 0.5f) {
            return true;
        }
        boolean fullRegion = image.u() == 0f && image.v() == 0f
                && image.regionWidth() <= 0f && image.regionHeight() <= 0f;
        if (!fullRegion) {
            return true;
        }
        return ImageMasks.local(reference.get().id(), image.width(), image.height(), radius)
                .state() != ImageMasks.State.LOADING;
    }

    private void renderImage(ImageNode node, float opacity) {
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        String resolvedUrl = cache.resolvedTemplate(generation(), node.texture(),
                template -> Bindings.resolve(template, state));
        if (resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")) {
            RemoteImages.Entry entry = RemoteImages.resolve(resolvedUrl, node.sha256());
            if (entry.state() == RemoteImages.State.READY && entry.texture() != null) {
                float radius = node.style().radius();
                if (radius > 0.5f) {
                    ImageMasks.Variant mask = ImageMasks.remote(resolvedUrl, node.width(), node.height(), radius);
                    if (mask.state() == ImageMasks.State.READY) {
                        blitFull(mask.texture(), node);
                    } else if (mask.state() == ImageMasks.State.FAILED) {
                        blitFull(entry.texture(), node);
                    } else {
                        // 遮罩烘焙中：与加载中相同的圆角占位，不出方形帧
                        painter.fillRounded(node.x(), node.y(), node.width(), node.height(),
                                radius, 0x33FFFFFF, opacity);
                    }
                } else {
                    blitFull(entry.texture(), node);
                }
            } else {
                // 加载中/失败：半透明占位，避免空白闪烁
                int color = entry.state() == RemoteImages.State.LOADING ? 0x33FFFFFF : 0x66FF4444;
                painter.fillRounded(node.x(), node.y(), node.width(), node.height(),
                        Math.max(0f, node.style().radius()), color, opacity);
            }
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
        float texW = node.textureWidth();
        float texH = node.textureHeight();
        float regionW = node.regionWidth();
        float regionH = node.regionHeight();
        if (texW <= 0f || texH <= 0f || regionW <= 0f || regionH <= 0f) {
            int[] actual = TextureSizes.resolve(texture);
            if (texW <= 0f) {
                texW = actual[0];
            }
            if (texH <= 0f) {
                texH = actual[1];
            }
            if (regionW <= 0f) {
                regionW = texW;
            }
            if (regionH <= 0f) {
                regionH = texH;
            }
        }
        float u0 = node.u() / texW;
        float v0 = node.v() / texH;
        float u1 = (node.u() + regionW) / texW;
        float v1 = (node.v() + regionH) / texH;
        boolean fullRegion = u0 == 0f && v0 == 0f && u1 == 1f && v1 == 1f;
        if (node.style().radius() > 0.5f && fullRegion) {
            ImageMasks.Variant mask = ImageMasks.local(texture, node.width(), node.height(),
                    node.style().radius());
            if (mask.state() == ImageMasks.State.READY) {
                blitFull(mask.texture(), node);
                return;
            }
            if (mask.state() == ImageMasks.State.LOADING) {
                // 遮罩烘焙中：圆角占位，不出方形帧
                painter.fillRounded(node.x(), node.y(), node.width(), node.height(),
                        node.style().radius(), 0x33FFFFFF, opacity);
                return;
            }
            // FAILED：回退原图
        }
        // 26.2 的 blit 浮点参数顺序是 (u0, u1, v0, v1)，不是 (u0, v0, u1, v1)
        if (node.tint() != 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    Math.round(node.x()), Math.round(node.y()),
                    Math.round(node.u()), Math.round(node.v()),
                    Math.round(node.regionWidth()), Math.round(node.regionHeight()),
                    (int) texW, (int) texH, UiColors.withOpacity(node.tint(), 1f));
            return;
        }
        graphics.blit(texture,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.x() + node.width()), Math.round(node.y() + node.height()),
                u0, u1, v0, v1);
    }

    /** 整图采样 blit（遮罩/原图通用）；tint != 0 时走带色管线 blit。 */
    private void blitFull(Identifier texture, ImageNode node) {
        int tint = node.tint();
        int w = Math.round(node.width());
        int h = Math.round(node.height());
        if (tint != 0) {
            int[] dims = TextureSizes.resolve(texture);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    Math.round(node.x()), Math.round(node.y()),
                    0f, 0f, w, h, dims[0], dims[1], UiColors.withOpacity(tint, 1f));
            return;
        }
        // 26.2 的 blit 浮点参数顺序是 (u0, u1, v0, v1)：整图采样为 0,1,0,1
        graphics.blit(texture,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.x() + w), Math.round(node.y() + h),
                0f, 1f, 0f, 1f);
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
        boolean enabled = node.enabledNow();
        boolean active = enabled && (node.dragging() || node.hovered());
        int tint = enabled ? 0xFFFFFFFF : 0xFF808080;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                active ? SLIDER_HIGHLIGHTED_SPRITE : SLIDER_SPRITE,
                Math.round(node.x()), Math.round(node.y()),
                Math.round(node.width()), Math.round(node.height()), tint);

        float handleWidth = 8f;
        float ratio = (float) Math.max(0.0, Math.min(1.0, node.ratio(state)));
        float handleX = node.x() + ratio * (node.width() - handleWidth);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                active ? SLIDER_HANDLE_HIGHLIGHTED_SPRITE : SLIDER_HANDLE_SPRITE,
                Math.round(handleX), Math.round(node.y()),
                Math.round(handleWidth), Math.round(node.height()), tint);

        String label = node.label(state);
        if (!label.isEmpty()) {
            int textWidth = font.width(label);
            graphics.text(font, label,
                    Math.round(node.x() + (node.width() - textWidth) / 2f),
                    Math.round(node.y() + (node.height() - font.lineHeight) / 2f),
                    UiColors.withOpacity(enabled ? node.textColor() : 0xFF808080, opacity), true);
        }
    }

    // ---------- 进度条（支持客户端插值） ----------

    private void renderProgress(ProgressNode node, float opacity) {
        drawSurface(node, opacity);
        if (node.width() <= 0 || node.height() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        double display = node.displayValue(state, now);
        float ratio = (float) Math.max(0.0, Math.min(1.0, node.ratioOf(display)));

        float inset = node.style().borderWidth();
        float x = node.x() + inset;
        float y = node.y() + inset;
        float width = Math.max(0f, node.width() - inset * 2f);
        float height = Math.max(0f, node.height() - inset * 2f);

        float fillX = x;
        float fillY = y;
        float fillW = width;
        float fillH = height;
        switch (node.direction()) {
            case LEFT_RIGHT -> fillW = width * ratio;
            case RIGHT_LEFT -> {
                fillW = width * ratio;
                fillX = x + width - fillW;
            }
            case TOP_BOTTOM -> fillH = height * ratio;
            case BOTTOM_TOP -> {
                fillH = height * ratio;
                fillY = y + height - fillH;
            }
        }

        if (fillW > 0.5f && fillH > 0.5f) {
            float radius = Math.max(0f, node.style().radius() - inset);
            if (node.fillGradientTo() != null) {
                painter.fillRoundedGradient(fillX, fillY, fillW, fillH, radius,
                        node.fillColor(), node.fillGradientTo(), opacity);
            } else {
                painter.fillRounded(fillX, fillY, fillW, fillH, radius, node.fillColor(), opacity);
            }
        }

        String label = node.label(state, display);
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
