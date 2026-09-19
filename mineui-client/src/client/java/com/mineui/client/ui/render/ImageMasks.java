package com.mineui.client.ui.render;

import com.mineui.client.MineUiClient;
import com.mineui.client.ui.remote.RemoteImages;
import com.mineui.ui.paint.ImageMaskMath;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片遮罩变体烘焙：把圆角/圆形 alpha 遮罩**预烘焙**成新纹理（一次），渲染时仍是单次 blit。
 * <p>
 * 变体键 = 源（URL / 贴图 id）+ 节点尺寸 + 节点 radius；断线/切服统一释放。
 * 源像素：远程取磁盘缓存字节重新解码；本地经资源管理器读取；精灵（sprite:）不支持遮罩。
 * 变体未就绪期间渲染器回退为未遮罩原图。
 */
public final class ImageMasks {

    public enum State {
        LOADING,
        READY,
        FAILED
    }

    private record Entry(State state, Identifier texture) {
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MineUI-ImageMask");
        thread.setDaemon(true);
        return thread;
    });

    private ImageMasks() {
    }

    /** 远程图片遮罩变体；未就绪返回 empty（渲染器回退原图）。 */
    public static Optional<Identifier> remote(String url, float nodeWidth, float nodeHeight, float radius) {
        return lookup(key("remote", url, nodeWidth, nodeHeight, radius),
                () -> {
                    byte[] raw = RemoteImages.cachedBytes(url);
                    return raw == null ? null : RemoteImages.decodeBytes(raw);
                }, nodeWidth, radius);
    }

    /** 本地资源图片遮罩变体；不支持（精灵/读取失败）返回 empty。 */
    public static Optional<Identifier> local(Identifier source, float nodeWidth, float nodeHeight, float radius) {
        return lookup(key("local", source.toString(), nodeWidth, nodeHeight, radius),
                () -> readResource(source), nodeWidth, radius);
    }

    /** 断线/切服：释放遮罩纹理句柄并清空缓存。 */
    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry entry : CACHE.values()) {
            if (entry.state() == State.READY && minecraft != null) {
                try {
                    minecraft.getTextureManager().release(entry.texture());
                } catch (Exception e) {
                    MineUiClient.LOGGER.debug("释放遮罩纹理失败: {}", e.getMessage());
                }
            }
        }
        CACHE.clear();
    }

    // ---------- 内部 ----------

    private interface SourceLoader {
        NativeImage load() throws Exception;
    }

    private static Optional<Identifier> lookup(String key, SourceLoader loader, float nodeWidth, float radius) {
        Entry cached = CACHE.get(key);
        if (cached != null) {
            return cached.state() == State.READY ? Optional.of(cached.texture()) : Optional.empty();
        }
        CACHE.put(key, new Entry(State.LOADING, null));
        EXECUTOR.execute(() -> bake(key, loader, nodeWidth, radius));
        return Optional.empty();
    }

    private static void bake(String key, SourceLoader loader, float nodeWidth, float radius) {
        try {
            NativeImage source = loader.load();
            int maskRadius = ImageMaskMath.maskRadius(radius, nodeWidth,
                    source.getWidth(), source.getHeight());
            int[] masked = ImageMaskMath.apply(source.getPixels(),
                    source.getWidth(), source.getHeight(), maskRadius);
            NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), true);
            image.copyFrom(source);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, masked[y * image.getWidth() + x]);
                }
            }
            Minecraft.getInstance().execute(() -> register(key, image));
        } catch (Throwable t) {
            CACHE.put(key, new Entry(State.FAILED, null));
            MineUiClient.LOGGER.debug("遮罩烘焙失败（{}）: {}", key, t.toString());
        }
    }

    private static void register(String key, NativeImage image) {
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mineui",
                    "image_mask/" + sha1Hex(key));
            DynamicTexture texture = new DynamicTexture(() -> "MineUI masked image", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(key, new Entry(State.READY, id));
        } catch (Exception e) {
            image.close();
            CACHE.put(key, new Entry(State.FAILED, null));
            MineUiClient.LOGGER.debug("遮罩纹理注册失败（{}）: {}", key, e.getMessage());
        }
    }

    private static NativeImage readResource(Identifier id) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElse(null);
        if (resource == null) {
            return null;
        }
        try (java.io.InputStream in = resource.open()) {
            return RemoteImages.decodeBytes(in.readAllBytes());
        }
    }

    private static String key(String kind, String source, float nodeWidth, float nodeHeight, float radius) {
        return kind + "|" + source + "|w" + Math.round(nodeWidth) + "x" + Math.round(nodeHeight)
                + "|r" + Math.round(radius);
    }

    private static String sha1Hex(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }
}
