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

    /**
     * 逐像素乘色（ARGB tint × ABGR 像素，含 alpha 通道）。
     * tint 为 0/纯白时返回原数组（避免无意义拷贝）。
     */
    public static int[] tint(int[] pixels, int tint) {
        if (pixels == null || tint == 0 || tint == 0xFFFFFFFF) {
            return pixels;
        }
        int ta = (tint >>> 24) & 0xFF;
        int tr = (tint >>> 16) & 0xFF;
        int tg = (tint >>> 8) & 0xFF;
        int tb = tint & 0xFF;
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int pa = (pixel >>> 24) & 0xFF;
            int pb = (pixel >>> 16) & 0xFF;
            int pg = (pixel >>> 8) & 0xFF;
            int pr = pixel & 0xFF;
            int a = pa * ta / 255;
            int r = pr * tr / 255;
            int g = pg * tg / 255;
            int b = pb * tb / 255;
            result[i] = (a << 24) | (b << 16) | (g << 8) | r;
        }
        return result;
    }
}
