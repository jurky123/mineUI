package com.mineui.client.ui.render;

import com.mineui.ui.paint.RoundedRect;
import com.mineui.ui.paint.UiColors;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 圆角/渐变/阴影等绘制原语。 */
public final class UiPainter {

    private final GuiGraphicsExtractor graphics;

    public UiPainter(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public void fillRounded(float x, float y, float w, float h, float radius, int color, float opacity) {
        int argb = UiColors.withOpacity(color, opacity);
        if (((argb >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) {
            return;
        }
        if (radius <= 0.5f) {
            graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb);
            return;
        }
        float[][] insets = RoundedRect.rowInsets(h, radius);
        for (int i = 0; i < insets.length; i++) {
            float rowY = y + i;
            int left = Math.round(x + insets[i][0]);
            int right = Math.round(x + w - insets[i][1]);
            if (right <= left) {
                continue;
            }
            graphics.fill(left, Math.round(rowY), right, Math.round(rowY + 1f), argb);
        }
    }

    /** 纵向渐变圆角矩形。 */
    public void fillRoundedGradient(float x, float y, float w, float h, float radius,
                                    int from, int to, float opacity) {
        int start = UiColors.withOpacity(from, opacity);
        int end = UiColors.withOpacity(to, opacity);
        if (w <= 0 || h <= 0 || (((start >>> 24) | (end >>> 24)) & 0xFF) == 0) {
            return;
        }
        float[][] insets = radius > 0.5f ? RoundedRect.rowInsets(h, radius) : null;
        int rows = Math.max(1, (int) Math.ceil(h));
        for (int i = 0; i < rows; i++) {
            float t = rows <= 1 ? 0f : i / (float) (rows - 1);
            int color = UiColors.lerp(start, end, t);
            float rowY = y + i;
            float leftInset = insets != null && i < insets.length ? insets[i][0] : 0f;
            float rightInset = insets != null && i < insets.length ? insets[i][1] : 0f;
            int left = Math.round(x + leftInset);
            int right = Math.round(x + w - rightInset);
            if (right <= left) {
                continue;
            }
            graphics.fill(left, Math.round(rowY), right, Math.round(rowY + 1f), color);
        }
    }

    /** 伪软阴影：多层外扩半透明圆角矩形。 */
    public void shadow(float x, float y, float w, float h, float radius,
                       int color, float size, float offsetY, float opacity) {
        if (size <= 0f || w <= 0 || h <= 0) {
            return;
        }
        int layers = Math.max(1, (int) Math.ceil(size));
        float step = size / layers;
        for (int i = layers; i >= 1; i--) {
            float grow = i * step;
            float alpha = opacity * 0.6f * (1f - (i - 1) / (float) layers);
            fillRounded(x - grow, y + offsetY - grow, w + 2f * grow, h + 2f * grow,
                    radius + grow, UiColors.withOpacity(color, alpha), 1f);
        }
    }
}
