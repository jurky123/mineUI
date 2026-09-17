package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 界面定义加载器。
 * <p>
 * 查找顺序：
 * <ol>
 *   <li>开发目录覆盖：{@code <config>/mineui/ui/<app>/<view>.json}（F9 热重载会清缓存）</li>
 *   <li>mod 内置资源：{@code assets/mineui/ui/<app>/<view>.json}</li>
 * </ol>
 * app/view 仅允许 {@code [a-z0-9_-]}，防止路径穿越；每次加载都会构造全新的节点树，
 * 避免复用上一次打开的滚动/动画等运行时状态。
 */
public final class UiDefinitionLoader {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private static final Map<String, CachedDefinition> CACHE = new ConcurrentHashMap<>();

    /** 缓存原始 JSON，节点树每次重新构造。 */
    private record CachedDefinition(String source, JsonObject json) {
    }

    private UiDefinitionLoader() {
    }

    public static UiDefinition load(String app, String view, Path devRoot) throws UiSpecException {
        requireSafe(app, "app");
        requireSafe(view, "view");
        String key = app + "/" + view;
        CachedDefinition cached = CACHE.get(key);
        if (cached == null) {
            cached = read(app, view, devRoot);
            CACHE.put(key, cached);
        }
        return new UiDefinition(app, view, cached.source(), UiSpecParser.parse(cached.json()));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** 开发覆盖文件路径：{@code <devRoot>/<app>/<view>.json}。 */
    public static Path devFile(String app, String view, Path devRoot) {
        return devRoot.resolve(app).resolve(view + ".json");
    }

    /**
     * 把内置定义复制到开发目录（显式调用，不覆盖已有文件）。
     *
     * @return true 表示本次新建了文件
     */
    public static boolean writeDevTemplate(String app, String view, Path devRoot) throws UiSpecException {
        requireSafe(app, "app");
        requireSafe(view, "view");
        Path target = devFile(app, view, devRoot);
        if (Files.exists(target)) {
            return false;
        }
        String resource = "/assets/mineui/ui/" + app + "/" + view + ".json";
        try (InputStream in = UiDefinitionLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new UiSpecException("导出开发模板失败: " + target + " (" + e.getMessage() + ")", e);
        }
    }

    private static CachedDefinition read(String app, String view, Path devRoot) throws UiSpecException {
        Path devFile = devFile(app, view, devRoot);
        if (Files.isRegularFile(devFile)) {
            try {
                String source = "dev:" + devFile;
                return new CachedDefinition(source, parseObject(Files.readString(devFile, StandardCharsets.UTF_8), source));
            } catch (IOException e) {
                throw new UiSpecException("读取开发界面失败: " + devFile + " (" + e.getMessage() + ")", e);
            }
        }

        String resource = "/assets/mineui/ui/" + app + "/" + view + ".json";
        try (InputStream in = UiDefinitionLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UiSpecException("未找到界面定义: " + app + "/" + view);
            }
            return new CachedDefinition("mod", parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8), "mod"));
        } catch (IOException e) {
            throw new UiSpecException("读取内置界面失败: " + resource + " (" + e.getMessage() + ")", e);
        }
    }

    private static JsonObject parseObject(String json, String source) throws UiSpecException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new UiSpecException("JSON 解析失败 (" + source + "): " + e.getMessage(), e);
        }
    }

    private static void requireSafe(String value, String kind) throws UiSpecException {
        if (value == null || !SAFE_NAME.matcher(value).matches()) {
            throw new UiSpecException("非法" + kind + "标识符: " + value);
        }
    }
}
