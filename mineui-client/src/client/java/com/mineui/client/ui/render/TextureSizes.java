package com.mineui.client.ui.render;

import com.mineui.client.MineUiClient;
import com.mineui.ui.util.PngDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 贴图真实像素尺寸（从 ResourceManager 读 PNG 头，不依赖纹理是否已上传 GPU）。
 * image 节点缺省 textureSize/region 时用它显示整张图。
 */
public final class TextureSizes {

    private static final int[] FALLBACK = {256, 256};
    private static final Map<Identifier, int[]> CACHE = new ConcurrentHashMap<>();

    private TextureSizes() {
    }

    public static int[] resolve(Identifier id) {
        int[] cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        int[] size = read(id);
        CACHE.put(id, size);
        return size;
    }

    private static int[] read(Identifier id) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return FALLBACK;
            }
            try (InputStream in = resource.get().open()) {
                int[] size = PngDimensions.read(in);
                return size != null ? size : FALLBACK;
            }
        } catch (Exception e) {
            MineUiClient.LOGGER.warn("读取贴图尺寸失败 {}: {}", id, e.getMessage());
            return FALLBACK;
        }
    }
}
