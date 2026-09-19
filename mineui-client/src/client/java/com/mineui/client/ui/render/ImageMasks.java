package com.mineui.client.ui.render;

import com.mineui.client.MineUiClient;
import com.mineui.client.ui.remote.RemoteImages;
import com.mineui.ui.paint.ImageMaskMath;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 变体状态：LOADING（烘焙中，渲染器画圆角占位且不推进旋转时钟）、READY（可 blit）、FAILED（回退原图）。 */
    public record Variant(State state, Identifier texture) {
    }

    private record Entry(State state, Identifier texture) {
    }

    /** 烘焙源字节上限（与远程下载硬上限一致，防止解码巨型资源）。 */
    static final long MAX_SOURCE_BYTES = 4L * 1024 * 1024;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MineUI-ImageMask");
        thread.setDaemon(true);
        return thread;
    });
    /** 连接代际：reset() 自增；在途烘焙完成后若代际已变则直接丢弃。 */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private ImageMasks() {
    }

    /** 远程图片变体（遮罩和/或着色）；LOADING/FAILED 时 texture 为 null。 */
    public static Variant remote(String url, float nodeWidth, float nodeHeight, float radius, int tint) {
        return lookup(key("remote", url, nodeWidth, nodeHeight, radius, tint),
                () -> {
                    byte[] raw = RemoteImages.cachedBytes(url);
                    if (raw == null || raw.length > MAX_SOURCE_BYTES) {
                        return null;
                    }
                    return RemoteImages.decodeBytes(raw);
                }, nodeWidth, radius, tint, GENERATION.get());
    }

    /** 本地资源图片变体；不支持（精灵/读取失败）为 FAILED。 */
    public static Variant local(Identifier source, float nodeWidth, float nodeHeight, float radius, int tint) {
        return lookup(key("local", source.toString(), nodeWidth, nodeHeight, radius, tint),
                () -> readResource(source), nodeWidth, radius, tint, GENERATION.get());
    }

    /** 断线/切服：先作废代际，再释放纹理句柄并清空缓存。 */
    public static void reset() {
        GENERATION.incrementAndGet();
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

    /** 代际是否已过期（断线/切服发生）。 */
    private static boolean stale(int generation) {
        return generation != GENERATION.get();
    }

    private static void fail(String key, int generation) {
        if (!stale(generation)) {
            CACHE.put(key, new Entry(State.FAILED, null));
        }
    }

    private static Variant lookup(String key, SourceLoader loader, float nodeWidth, float radius, int tint,
                                  int generation) {
        Entry cached = CACHE.get(key);
        if (cached != null) {
            return new Variant(cached.state(), cached.texture());
        }
        CACHE.put(key, new Entry(State.LOADING, null));
        EXECUTOR.execute(() -> bake(key, loader, nodeWidth, radius, tint, generation));
        return new Variant(State.LOADING, null);
    }

    private static void bake(String key, SourceLoader loader, float nodeWidth, float radius, int tint,
                             int generation) {
        NativeImage source = null;
        NativeImage image = null;
        try {
            if (stale(generation)) {
                return;
            }
            source = loader.load();
            if (source == null || stale(generation)) {
                return;
            }
            int maskRadius = radius > 0.5f
                    ? ImageMaskMath.maskRadius(radius, nodeWidth, source.getWidth(), source.getHeight())
                    : 0;
            int[] pixels = ImageMaskMath.apply(source.getPixels(),
                    source.getWidth(), source.getHeight(), maskRadius);
            if (tint != 0) {
                pixels = ImageMaskMath.tint(pixels, tint);
            }
            if (stale(generation)) {
                return;
            }
            image = new NativeImage(source.getWidth(), source.getHeight(), true);
            image.copyFrom(source);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, pixels[y * image.getWidth() + x]);
                }
            }
            NativeImage done = image;
            image = null;
            Minecraft.getInstance().execute(() -> register(key, done, generation));
        } catch (Throwable t) {
            fail(key, generation);
            MineUiClient.LOGGER.debug("变体烘焙失败（{}）: {}", key, t.toString());
        } finally {
            if (source != null) {
                source.close();
            }
            if (image != null) {
                image.close();
            }
        }
    }

    private static void register(String key, NativeImage image, int generation) {
        if (stale(generation)) {
            image.close();
            return;
        }
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mineui",
                    "image_mask/" + sha1Hex(key));
            DynamicTexture texture = new DynamicTexture(() -> "MineUI image variant", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            if (!stale(generation)) {
                CACHE.put(key, new Entry(State.READY, id));
            } else {
                Minecraft.getInstance().getTextureManager().release(id);
            }
        } catch (Exception e) {
            image.close();
            fail(key, generation);
            MineUiClient.LOGGER.debug("变体纹理注册失败（{}）: {}", key, e.getMessage());
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

    private static String key(String kind, String source, float nodeWidth, float nodeHeight,
                              float radius, int tint) {
        return kind + "|" + source + "|w" + Math.round(nodeWidth) + "x" + Math.round(nodeHeight)
                + "|r" + Math.round(radius) + "|t" + String.format("%08x", tint);
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
