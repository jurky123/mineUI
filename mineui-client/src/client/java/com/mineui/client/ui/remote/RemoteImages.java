package com.mineui.client.ui.remote;

import com.mineui.client.MineUiClient;
import com.mineui.protocol.msg.RemoteImagePolicy;
import com.mineui.ui.util.RemoteUrlGuard;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "MineUI-RemoteImage");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
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
            return cached;
        }
        if (cached != null && cached.state() == State.FAILED) {
            return cached;
        }
        RemoteUrlGuard.Result check = RemoteUrlGuard.check(url, policy);
        if (check != RemoteUrlGuard.Result.OK) {
            Entry failed = new Entry(State.FAILED, null, 0, 0);
            ENTRIES.put(url, failed);
            MineUiClient.LOGGER.warn("远程图片被拒绝（{}）: {}", check, url);
            return failed;
        }
        Entry loading = new Entry(State.LOADING, null, 0, 0);
        ENTRIES.put(url, loading);
        EXECUTOR.execute(() -> download(url, sha256));
        return loading;
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
        ENTRIES.clear();
    }

    // ---------- 异步加载 ----------

    private static void download(String url, String sha256) {
        try {
            byte[] raw = loadBytes(url);
            if (sha256 != null && !sha256.isBlank() && !sha256.equalsIgnoreCase(sha256Hex(raw))) {
                throw new IOException("sha256 校验失败");
            }
            NativeImage image = decode(raw);
            Minecraft.getInstance().execute(() -> register(url, image));
        } catch (Exception e) {
            ENTRIES.put(url, new Entry(State.FAILED, null, 0, 0));
            MineUiClient.LOGGER.warn("远程图片加载失败 {}: {}", url, e.getMessage());
        }
    }

    private static void register(String url, NativeImage image) {
        try {
            Identifier id = Identifier.fromNamespaceAndPath("mineui",
                    "remote/" + sha1Hex(url));
            DynamicTexture texture = new DynamicTexture(() -> "MineUI remote image", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            ENTRIES.put(url, new Entry(State.READY, id, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            image.close();
            ENTRIES.put(url, new Entry(State.FAILED, null, 0, 0));
            MineUiClient.LOGGER.warn("远程图片注册失败 {}: {}", url, e.getMessage());
        }
    }

    private static byte[] loadBytes(String url) throws IOException, InterruptedException {
        String current = url;
        for (int hop = 0; hop <= 3; hop++) {
            URI uri = URI.create(current);
            if (RemoteUrlGuard.check(current, policy) != RemoteUrlGuard.Result.OK) {
                throw new IOException("URL 未通过策略校验");
            }
            requirePublicAddress(uri.getHost());

            Path cached = cacheFile(current);
            if (Files.isRegularFile(cached)) {
                return Files.readAllBytes(cached);
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "MineUI/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
            byte[] bytes = readLimited(response.body(), policy.maxBytes());
            Files.createDirectories(cached.getParent());
            Files.write(cached, bytes);
            pruneCache();
            return bytes;
        }
        throw new IOException("重定向次数过多");
    }

    private static void requirePublicAddress(String host) throws IOException {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (RemoteUrlGuard.isPrivateAddress(address)) {
                    throw new IOException("解析到受限地址: " + address.getHostAddress());
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("域名解析失败: " + host);
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
        try {
            return NativeImage.read(bytes);
        } catch (IOException pngFailure) {
            // 非 PNG（如 JPEG）：用 ImageIO 解码后转成 PNG 再交给 NativeImage，避免手工像素格式转换
            try {
                var buffered = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (buffered == null) {
                    throw pngFailure;
                }
                if (buffered.getWidth() > 4096 || buffered.getHeight() > 4096 || buffered.getWidth() <= 0) {
                    throw new IOException("图片尺寸非法");
                }
                java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(buffered, "png", png);
                return NativeImage.read(png.toByteArray());
            } catch (IOException e) {
                throw pngFailure;
            }
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
            if (total <= policy.cacheBytes()) {
                return;
            }
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
                if (total <= policy.cacheBytes()) {
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
