package com.mineui.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * 采样器可选的动态纹理（FR-15 图片过滤）。
 * <p>
 * 原版 {@link DynamicTexture} 写死最近邻；像素风封面放大时需要它，
 * 而 {@code filter: "linear"} 的页面需要线性。必须在渲染线程构造
 * （SamplerCache 创建 GL 采样器），调用方保证（变体注册走主线程）。
 */
final class FilteredDynamicTexture extends DynamicTexture {

    private final GpuSampler sampler;

    FilteredDynamicTexture(String name, NativeImage pixels, FilterMode filter) {
        super(() -> name, pixels);
        this.sampler = RenderSystem.getSamplerCache().getRepeat(filter);
    }

    @Override
    public GpuSampler getSampler() {
        return sampler;
    }
}
