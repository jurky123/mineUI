package com.mineui.client.ui.render;

import com.mineui.ui.util.GenerationCache;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 渲染期缓存（随 Screen 持久，状态代数变化时失效）：
 * 把 {@code {state.x}} 模板解析结果缓存，避免每帧重复解析与 Identifier 构造。
 */
public final class RenderCache {

    /** 解析后的贴图引用：原版精灵（九宫格）或普通纹理。 */
    public record TextureRef(Identifier id, boolean sprite) {
    }

    private final GenerationCache<String> resolvedTemplates = new GenerationCache<>();
    private final GenerationCache<Optional<TextureRef>> textures = new GenerationCache<>();
    private final GenerationCache<String> itemIds = new GenerationCache<>();
    private final GenerationCache<List<String>> modelStrings = new GenerationCache<>();

    /** 解析（绑定替换）后的贴图字符串；同一代内只解析一次。 */
    public String resolvedTemplate(int generation, String template, Function<String, String> resolver) {
        String result = resolvedTemplates.get(generation, template, resolver);
        return result == null ? "" : result;
    }

    public Optional<TextureRef> texture(int generation, String template, Function<String, Optional<TextureRef>> resolver) {
        Optional<TextureRef> result = textures.get(generation, template, resolver);
        return result == null ? Optional.empty() : result;
    }

    public String itemId(int generation, String template, Function<String, String> resolver) {
        String result = itemIds.get(generation, template, resolver);
        return result == null ? "" : result;
    }

    public List<String> modelStrings(int generation, List<String> templates, Function<String, String> resolver) {
        String key = String.join("\u0000", templates);
        List<String> result = modelStrings.get(generation, key, ignored -> {
            List<String> resolved = new ArrayList<>(templates.size());
            for (String template : templates) {
                resolved.add(resolver.apply(template));
            }
            return List.copyOf(resolved);
        });
        return result == null ? List.of() : result;
    }
}
