package com.mineui.client.ui.remote;

import com.mineui.client.MineUiClient;
import com.mineui.protocol.msg.RemoteImagePolicy;
import com.mineui.ui.util.RemoteUrlGuard;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 远程图片（封面/头像）：HTTPS 直连 + 服务端白名单策略 + 私网拦截 + 内存/磁盘缓存 + 异步解码。
 * <p>
 * 渲染线程只做 map 查询与 blit；下载与解码在线程池，完成后回主线程注册动态纹理。
 */
public final class RemoteImages {

    public enum State {
        LOADING,
        READY,
        FAILED
    }

    public record Entry(State state, Identifier texture, int width, int height) {
    }

    /** 域名解析超时（部分被墙/污染的域名解析会长时间阻塞，不能拖住下载线程）。 */
    private static final long DNS_TIMEOUT_SECONDS = 5L;
    /** 兜底看门狗：超过该时长仍未完成（含线程卡死/任务未执行）一律判失败，不再永久"加载中"。 */
    private static final long LOAD_TIMEOUT_MILLIS = 15_000L;
    /**
     * 客户端硬上限：服务端 policy 只能收紧这些值，不能放大。
     * 远程图片是客户端直接接触不受信任内容的边界，不能只信服务端下发。
     */
    private static final long MAX_DOWNLOAD_BYTES = 4L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 4096L * 4096L;
    private static final long MAX_CACHE_BYTES = 128L * 1024 * 1024;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, Long> STARTED = new ConcurrentHashMap<>();
    /** 已提示过失败的 URL，避免重复刷屏。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** 连接代数：reset() 自增，用于丢弃断线/切服后才完成的下载与解码结果。 */
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "MineUI-RemoteImage");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService RESOLVER = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "MineUI-RemoteImage-DNS");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static volatile RemoteImagePolicy policy = RemoteImagePolicy.disabled();

    private RemoteImages() {
    }

    public static void setPolicy(RemoteImagePolicy newPolicy) {
        RemoteImagePolicy normalized = newPolicy == null ? RemoteImagePolicy.disabled() : newPolicy;
        boolean changed = !normalized.equals(policy);
        policy = normalized;
        if (changed) {
            MineUiClient.LOGGER.info("远程图片策略: enabled={} domains={} maxBytes={}",
                    policy.enabled(), policy.allowedDomains(), policy.maxBytes());
        } else {
            MineUiClient.LOGGER.debug("远程图片策略未变化");
        }
    }

    public static RemoteImagePolicy policy() {
        return policy;
    }

    /** 渲染线程调用：返回当前状态；未加载且允许时触发异步加载。 */
    public static Entry resolve(String url, String sha256) {
        Entry cached = ENTRIES.get(url);
        if (cached != null && cached.state() != State.FAILED) {
            if (cached.state() == State.LOADING && loadExpired(url)) {
                Entry failed = new Entry(State.FAILED, null, 0, 0);
                ENTRIES.put(url, failed);
                reportFailure(url, "下载超时（超过 " + (LOAD_TIMEOUT_MILLIS / 1000) + " 秒）");
                return failed;
            }
            return cached;
        }
        if (cached != null) {
            return cached;
        }
        RemoteUrlGuard.Result check = RemoteUrlGuard.check(url, policy);
        if (check != RemoteUrlGuard.Result.OK) {
            Entry failed = new Entry(State.FAILED, null, 0, 0);
            ENTRIES.put(url, failed);
            reportFailure(url, "被拒绝（" + check + "）");
            return failed;
        }
        Entry loading = new Entry(State.LOADING, null, 0, 0);
        ENTRIES.put(url, loading);
        STARTED.put(url, System.currentTimeMillis());
        MineUiClient.LOGGER.info("远程图片开始下载: {}", url);
        int generation = GENERATION.get();
        EXECUTOR.execute(() -> download(url, sha256, generation));
        return loading;
    }

    private static boolean loadExpired(String url) {
        Long started = STARTED.get(url);
        return started != null && System.currentTimeMillis() - started > LOAD_TIMEOUT_MILLIS;
    }

    /** 断线/切服：释放纹理句柄并清空内存缓存（磁盘缓存保留，受 LRU 上限约束）。 */
    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Entry entry : ENTRIES.values()) {
            if (entry.state() == State.READY && entry.texture() != null && minecraft != null) {
                try {
                    minecraft.getTextureManager().release(entry.texture());
                } catch (Exception e) {
                    MineUiClient.LOGGER.debug("释放远程图片纹理失败: {}", e.getMessage());
                }
            }
        }
        // 先作废代数，再清空缓存：断线后完成的下载/解码不会再写回
        GENERATION.incrementAndGet();
        ENTRIES.clear();
        STARTED.clear();
        REPORTED.clear();
    }

    // ---------- 异步加载 ----------

    private static void download(String url, String sha256, int generation) {
        try {
            if (generation != GENERATION.get()) {
                return;
            }
            MineUiClient.LOGGER.info("远程图片下载中: {}", url);
            byte[] raw = loadBytes(url, generation);
            if (generation != GENERATION.get()) {
                return;
            }
            if (sha256 != null && !sha256.isBlank() && !sha256.equalsIgnoreCase(sha256Hex(raw))) {
                throw new IOException("sha256 校验失败");
            }
            requireAcceptableSize(raw);
            NativeImage image = decode(raw);
            Minecraft.getInstance().execute(() -> register(url, image, generation));
        } catch (Exception e) {
            if (generation == GENERATION.get()) {
                ENTRIES.put(url, new Entry(State.FAILED, null, 0, 0));
                reportFailure(url, e.getMessage());
            }
        }
    }

    /** 记录并提示一次失败（含原因），同一 URL 每会话只提示一次。 */
    private static void reportFailure(String url, String reason) {
        if (!REPORTED.add(url)) {
            return;
        }
        String detail = reason == null || reason.isBlank() ? "未知原因" : reason;
        MineUiClient.LOGGER.warn("远程图片加载失败 {}: {}", url, detail);
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("[MineUI] 远程图片加载失败（" + detail + "）: " + url));
            }
        });
    }

    private static void register(String url, NativeImage image, int generation) {
        if (generation != GENERATION.get()) {
            // 断线/切服后返回的解码结果：直接释放，避免把旧服务器内容写回新会话
            image.close();
            return;
        }
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mineui",
                    "remote/" + sha1Hex(url));
            DynamicTexture texture = new DynamicTexture(() -> "MineUI remote image", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            ENTRIES.put(url, new Entry(State.READY, id, image.getWidth(), image.getHeight()));
            MineUiClient.LOGGER.info("远程图片已加载: {} ({}x{})", url, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            image.close();
            ENTRIES.put(url, new Entry(State.FAILED, null, 0, 0));
            reportFailure(url, "注册失败: " + e.getMessage());
        }
    }

    private static byte[] loadBytes(String url, int generation) throws IOException, InterruptedException {
        String current = url;
        for (int hop = 0; hop <= 3; hop++) {
            if (generation != GENERATION.get()) {
                throw new IOException("已断开连接，取消下载");
            }
            URI uri = URI.create(current);
            if (RemoteUrlGuard.check(current, policy) != RemoteUrlGuard.Result.OK) {
                throw new IOException("URL 未通过策略校验");
            }
            requirePublicAddress(uri.getHost());

            Path cached = cacheFile(current);
            if (Files.isRegularFile(cached)) {
                if (Files.size(cached) <= effectiveDownloadBytes()) {
                    touch(cached);
                    return Files.readAllBytes(cached);
                }
                Files.deleteIfExists(cached);
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "MineUI/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (generation != GENERATION.get()) {
                response.body().close();
                throw new IOException("已断开连接，取消下载");
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse(null);
                response.body().close();
                if (location == null) {
                    throw new IOException("重定向缺少 Location");
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("HTTP " + status);
            }
            byte[] bytes = readLimited(response.body(), effectiveDownloadBytes());
            requireAcceptableSize(bytes);
            Files.createDirectories(cached.getParent());
            Files.write(cached, bytes);
            pruneCache();
            return bytes;
        }
        throw new IOException("重定向次数过多");
    }

    /** 单张下载上限取服务端 policy 与客户端硬上限的较小值。 */
    private static long effectiveDownloadBytes() {
        return Math.min(policy.maxBytes(), MAX_DOWNLOAD_BYTES);
    }

    /** 磁盘缓存上限取服务端 policy 与客户端硬上限的较小值。 */
    private static long effectiveCacheBytes() {
        return Math.min(policy.cacheBytes(), MAX_CACHE_BYTES);
    }

    private static void requirePublicAddress(String host) throws IOException {
        InetAddress[] addresses;
        try {
            addresses = RESOLVER.submit(() -> InetAddress.getAllByName(host))
                    .get(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("域名解析超时: " + host);
        } catch (ExecutionException e) {
            throw new IOException("域名解析失败: " + host, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断");
        }
        for (InetAddress address : addresses) {
            if (RemoteUrlGuard.isPrivateAddress(address)) {
                throw new IOException("解析到受限地址: " + address.getHostAddress());
            }
        }
    }

    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("超过大小上限 " + maxBytes + "B");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static NativeImage decode(byte[] bytes) throws IOException {
        NativeImage image;
        try {
            image = NativeImage.read(bytes);
        } catch (IOException pngFailure) {
            // 非 PNG（如 JPEG）：用 ImageIO 解码后转成 PNG 再交给 NativeImage，避免手工像素格式转换
            try {
                image = readOther(bytes);
            } catch (IOException e) {
                throw pngFailure;
            }
        }
        // 统一在注册纹理前校验尺寸/像素（PNG 也走这里），超限立即释放原生内存
        try {
            requireDimensions(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            image.close();
            throw e;
        }
        return image;
    }

    /** JPEG 等非 PNG：先按元数据校验尺寸再整图解码，避免超大图占用内存。 */
    private static NativeImage readOther(byte[] bytes) throws IOException {
        try (javax.imageio.stream.ImageInputStream input =
                     javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("无法读取图片流");
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("不支持的图片格式");
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                requireDimensions(reader.getWidth(0), reader.getHeight(0));
                java.awt.image.BufferedImage buffered = reader.read(0);
                java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(buffered, "png", png);
                return NativeImage.read(png.toByteArray());
            } finally {
                reader.dispose();
            }
        }
    }

    /** 解码前的文件头尺寸预检：PNG/JPEG 超限直接拒绝，避免解压炸弹进入内存。 */
    private static void requireAcceptableSize(byte[] bytes) throws IOException {
        if (bytes.length >= 24 && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            requireDimensions(readIntBigEndian(bytes, 16), readIntBigEndian(bytes, 20));
            return;
        }
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            try (javax.imageio.stream.ImageInputStream input =
                         javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                if (input == null) {
                    return;
                }
                java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    return;
                }
                javax.imageio.ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    requireDimensions(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    /** 尺寸/像素硬上限校验（单边 ≤ {@link #MAX_IMAGE_DIMENSION}，总像素 ≤ {@link #MAX_IMAGE_PIXELS}）。 */
    private static void requireDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IOException("图片尺寸非法: " + width + "x" + height);
        }
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new IOException("图片尺寸超限: " + width + "x" + height
                    + "（单边 ≤ " + MAX_IMAGE_DIMENSION + "，总像素 ≤ " + MAX_IMAGE_PIXELS + "）");
        }
    }

    private static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    /** 缓存命中的 LRU 语义：更新修改时间，淘汰时据此判断"最近访问"。 */
    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            MineUiClient.LOGGER.debug("刷新图片缓存时间失败: {}", e.getMessage());
        }
    }

    // ---------- 磁盘缓存（LRU 上限） ----------

    private static Path cacheDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mineui").resolve("cache").resolve("images");
    }

    private static Path cacheFile(String url) {
        return cacheDir().resolve(sha1Hex(url));
    }

    private static void pruneCache() {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> list = files.filter(Files::isRegularFile).toList();
            Map<Path, Long> sizes = new HashMap<>();
            long total = 0;
            for (Path file : list) {
                long size = Files.size(file);
                sizes.put(file, size);
                total += size;
            }
            long limit = effectiveCacheBytes();
            if (total <= limit) {
                return;
            }
            // 命中时已 touch（更新修改时间），这里按"最近访问时间"淘汰即为 LRU
            List<Path> byAge = list.stream()
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            for (Path file : byAge) {
                if (total <= limit) {
                    break;
                }
                long size = sizes.getOrDefault(file, 0L);
                Files.deleteIfExists(file);
                total -= size;
            }
        } catch (IOException e) {
            MineUiClient.LOGGER.warn("清理图片缓存失败: {}", e.getMessage());
        }
    }

    private static String sha1Hex(String value) {
        return digest("SHA-1", value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] value) {
        return digest("SHA-256", value);
    }

    private static String digest(String algorithm, byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance(algorithm).digest(value);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " 不可用", e);
        }
    }
}
