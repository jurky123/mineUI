package com.mineui.ui.paint;

/**
 * 图片遮罩像素计算（纯函数，便于单测）。
 * <p>
 * 输入 ABGR 打包像素（alpha 在高位，与 {@code NativeImage.getPixel} 一致），
 * 按圆角矩形扫描线把区域外像素 alpha 置 0；radius ≈ min(宽,高)/2 即圆形。
 */
public final class ImageMaskMath {

    private ImageMaskMath() {
    }

    /** 返回遮罩后的像素副本；radius ≤ 0 或尺寸不符时原样返回。 */
    public static int[] apply(int[] pixels, int width, int height, float radiusPx) {
        if (radiusPx <= 0.5f || width <= 0 || height <= 0
                || pixels == null || pixels.length != width * height) {
            return pixels;
        }
        float[][] insets = RoundedRect.rowInsets(height, radiusPx);
        int[] result = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            int left = Math.round(insets[y][0]);
            int rightStart = Math.max(left, width - Math.round(insets[y][1]));
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[row + x];
                result[row + x] = (x < left || x >= rightStart) ? (pixel & 0x00FFFFFF) : pixel;
            }
        }
        return result;
    }

    /**
     * 按节点空间 radius 计算纹理空间遮罩半径（随纹理/节点尺寸比例缩放，钳制到纹理半边）。
     */
    public static int maskRadius(float radius, float nodeWidth, int texWidth, int texHeight) {
        if (radius <= 0f || nodeWidth <= 0f || texWidth <= 0 || texHeight <= 0) {
            return 0;
        }
        int radiusPx = Math.round(radius * texWidth / nodeWidth);
        int limit = Math.min(texWidth, texHeight) / 2;
        return Math.max(0, Math.min(radiusPx, limit));
    }
}
