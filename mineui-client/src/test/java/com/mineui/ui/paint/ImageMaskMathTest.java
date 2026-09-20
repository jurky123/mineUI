package com.mineui.ui.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ImageMaskMathTest {

    @Test
    void maskClearsAlphaOutsideCircle() {
        // 4x4，radius=2（半边）→ 圆形：四角置透明
        int[] pixels = new int[16];
        java.util.Arrays.fill(pixels, 0xFFFF0000); // 不透明红（alpha 高位）
        int[] masked = ImageMaskMath.apply(pixels, 4, 4, 2f);

        assertEquals(0x00FF0000, masked[0], "左上角应透明");
        assertEquals(0x00FF0000, masked[3], "右上角应透明");
        assertEquals(0xFF000000 | (pixels[5] & 0x00FFFFFF), masked[5], "中心区域保持不透明");
        // 每行中心区域不透明
        assertEquals(0xFFFF0000, masked[6]);
    }

    @Test
    void zeroRadiusReturnsOriginal() {
        int[] pixels = new int[8];
        java.util.Arrays.fill(pixels, 0xFFFF0000);
        assertArrayEquals(pixels, ImageMaskMath.apply(pixels, 4, 2, 0f));
    }

    @Test
    void tintAppliesExactlyOnce() {
        // 半灰 tint 只乘一次：0xFF808080 × 不透明红 = 0xFF800000
        int[] pixels = {0xFFFF0000};
        int[] once = ImageMaskMath.tint(pixels, 0xFF808080);
        assertEquals(0xFF800000, once[0]);
        // 半透明 tint：alpha 通道同样只乘一次（255×128/255=128）
        int[] half = ImageMaskMath.tint(new int[]{0xFFFFFFFF}, 0x80FFFFFF);
        assertEquals(0x80FFFFFF, half[0]);
    }

    @Test
    void maskRadiusScalesAndClamps() {
        assertEquals(2, ImageMaskMath.maskRadius(1f, 4f, 8, 8), "节点 4px 半径 1 → 纹理 8px 半径 2");
        assertEquals(4, ImageMaskMath.maskRadius(9f, 8f, 8, 8), "钳制到纹理半边 4");
        assertEquals(0, ImageMaskMath.maskRadius(0f, 8f, 8, 8));
        assertEquals(0, ImageMaskMath.maskRadius(2f, 0f, 8, 8), "节点宽 0 → 0");
    }
}
