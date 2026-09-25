package com.mineui.client.ui.local;

import com.mineui.client.MineUiClient;
import com.mineui.client.ui.remote.RemoteImages;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地图片来源（FR-19）。
 * <p>
 * 页面写 {@code "texture": "{local.<ns>.<key>}"} 时，MineUI 调用同命名空间 provider 的
 * {@code image(key)} 取字节；本类负责异步解码、按 {@code (ns, key, generation)} 缓存、
 * 结构代数变化时失效、断线/页面关闭时释放纹理。不走网络/远程策略/域名白名单。
 */
public final class LocalImages {

    public enum State {
        /** 不是本地图 / provider 无图：调用方回退到 URL 逻辑。 */
        NONE,
        LOADING,
        READY,
        FAILED
    }

    /** 渲染结果：NONE/FAILED 时 texture 为 null。 */
    public record Entry(State state, Identifier texture, int width, int height) {
        static final Entry NONE = new Entry(State.NONE, null, 0, 0);
    }

    /** 单图硬上限（FR-19：2 MiB/图）。 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private record CacheEntry(State state, Identifier texture, int width, int height, long generation) {
    }

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    /** 已提示过的不可用项，避免重复刷日志。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** 连接代数：reset() 自增，用于丢弃断线后才完成的解码结果。 */
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MineUI-LocalImage");
        thread.setDaemon(true);
        return thread;
    });

    private LocalImages() {
    }

    /**
     * 渲染线程调用：解析 {@code <ns>.<key>}。
     * <p>
     * 返回 {@link State#NONE}/{@link State#FAILED} 表示调用方应回退到既有 URL/贴图逻辑；
     * {@link State#LOADING} 期间渲染占位；{@link State#READY} 用返回的纹理解析尺寸绘制。
     */
    public static Entry resolve(String path) {
        int dot = path == null ? -1 : path.indexOf('.');
        if (dot <= 0 || dot >= path.length() - 1) {
            return Entry.NONE;
        }
        String namespace = path.substring(0, dot);
        long generation = MineUiLocalBridge.namespaceGeneration(namespace);

        CacheEntry cached = CACHE.get(path);
        if (cached != null && cached.generation() == generation) {
            return new Entry(cached.state(), cached.texture(), cached.width(), cached.height());
        }
        if (cached != null) {
            release(cached);
            CACHE.remove(path, cached);
        }

        byte[] bytes = MineUiLocalBridge.image(path);
        if (bytes == null) {
            // provider 未注册/无图：本代数内记 NONE，不回退重试，避免每帧调用
            CACHE.put(path, new CacheEntry(State.NONE, null, 0, 0, generation));
            return Entry.NONE;
        }
        if (bytes.length > MAX_BYTES) {
            reportOnce(path, "超过 2 MiB 上限 (" + bytes.length + "B)");
            CACHE.put(path, new CacheEntry(State.FAILED, null, 0, 0, generation));
            return new Entry(State.FAILED, null, 0, 0);
        }
        CACHE.put(path, new CacheEntry(State.LOADING, null, 0, 0, generation));
        int token = GENERATION.get();
        EXECUTOR.execute(() -> decode(path, bytes, generation, token));
        return new Entry(State.LOADING, null, 0, 0);
    }

    /** 断线/切服：释放本地图片纹理并清空缓存（先作废代数，再丢弃在途结果）。 */
    public static void reset() {
        for (CacheEntry entry : CACHE.values()) {
            release(entry);
        }
        GENERATION.incrementAndGet();
        CACHE.clear();
        REPORTED.clear();
    }

    // ---------- 内部：异步解码 ----------

    private static void decode(String path, byte[] bytes, long generation, int token) {
        NativeImage image = null;
        try {
            image = RemoteImages.decodeBytes(bytes);
            NativeImage decoded = image;
            image = null;
            Minecraft.getInstance().execute(() -> register(path, decoded, generation, token));
        } catch (Exception e) {
            if (token == GENERATION.get() && CACHE.containsKey(path)) {
                CACHE.put(path, new CacheEntry(State.FAILED, null, 0, 0, generation));
                reportOnce(path, e.getMessage());
            }
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static void register(String path, NativeImage image, long generation, int token) {
        CacheEntry current = CACHE.get(path);
        if (token != GENERATION.get() || current == null
                || current.state() != State.LOADING || current.generation() != generation) {
            // 断线/已失效：丢弃解码结果
            image.close();
            return;
        }
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mineui", "local/" + sha1Hex(path));
            DynamicTexture texture = new DynamicTexture(() -> "MineUI local image", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            CACHE.put(path, new CacheEntry(State.READY, id, image.getWidth(), image.getHeight(), generation));
        } catch (Exception e) {
            image.close();
            CACHE.put(path, new CacheEntry(State.FAILED, null, 0, 0, generation));
            reportOnce(path, "注册失败: " + e.getMessage());
        }
    }

    private static void release(CacheEntry entry) {
        if (entry.state() != State.READY || entry.texture() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        try {
            minecraft.getTextureManager().release(entry.texture());
        } catch (Exception e) {
            MineUiClient.LOGGER.debug("释放本地图片纹理失败: {}", e.getMessage());
        }
    }

    private static void reportOnce(String path, String reason) {
        String detail = reason == null || reason.isBlank() ? "未知原因" : reason;
        if (REPORTED.add(path + "|" + detail)) {
            MineUiClient.LOGGER.debug("本地图片不可用 {}: {}", path, detail);
        }
    }

    private static String sha1Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }
}
