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

/**
 * 界面定义加载器。
 * <p>
 * 查找顺序：
 * <ol>
 *   <li>开发目录覆盖：{@code <config>/mineui/ui/<app>/<view>.json}（F9 热重载会清缓存）</li>
 *   <li>mod 内置资源：{@code assets/mineui/ui/<app>/<view>.json}</li>
 * </ol>
 */
public final class UiDefinitionLoader {

    private static final Map<String, UiDefinition> CACHE = new ConcurrentHashMap<>();

    private UiDefinitionLoader() {
    }

    public static UiDefinition load(String app, String view, Path devRoot) throws UiSpecException {
        String key = app + "/" + view;
        UiDefinition cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        UiDefinition definition = read(app, view, devRoot);
        CACHE.put(key, definition);
        return definition;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static UiDefinition read(String app, String view, Path devRoot) throws UiSpecException {
        Path devFile = devRoot.resolve(app).resolve(view + ".json");
        if (Files.isRegularFile(devFile)) {
            try {
                return parse(Files.readString(devFile, StandardCharsets.UTF_8), app, view, "dev:" + devFile);
            } catch (IOException e) {
                throw new UiSpecException("读取开发界面失败: " + devFile + " (" + e.getMessage() + ")", e);
            }
        }

        String resource = "/assets/mineui/ui/" + app + "/" + view + ".json";
        try (InputStream in = UiDefinitionLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UiSpecException("未找到界面定义: " + app + "/" + view);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), app, view, "mod");
        } catch (IOException e) {
            throw new UiSpecException("读取内置界面失败: " + resource + " (" + e.getMessage() + ")", e);
        }
    }

    private static UiDefinition parse(String json, String app, String view, String source) throws UiSpecException {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return new UiDefinition(app, view, source, UiSpecParser.parse(object));
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new UiSpecException("JSON 解析失败 (" + source + "): " + e.getMessage(), e);
        }
    }
}
