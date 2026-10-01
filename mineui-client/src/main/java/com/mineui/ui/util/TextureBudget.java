package com.mineui.ui.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 已解码图片纹理的内存预算策略（按像素字节 LRU）。
 * <p>
 * 纯函数：只根据「key + 像素字节 + 最近使用时间」决定应淘汰哪些 key，不触碰任何纹理句柄，
 * 便于单测；调用方负责在渲染线程释放被淘汰纹理。
 * <p>
 * 最近 {@code activeGraceMillis} 内使用过的项视为活跃（在屏/在 HUD），不淘汰——
 * 避免当前可见图像被反复释放又重载。
 */
public final class TextureBudget {

    /** 单个在册项：key、像素字节（宽×高×4）、最近使用时间（毫秒）。 */
    public record Usage(String key, long bytes, long lastUsedMillis) {
    }

    private TextureBudget() {
    }

    /**
     * 在总字节超过 {@code maxBytes} 时，按最久未用优先选出应淘汰的 key；
     * 跳过最近 {@code activeGraceMillis} 内使用过的活跃项。
     *
     * @param usages            当前在册项
     * @param maxBytes          内存预算（{@code <= 0} 表示不限制）
     * @param nowMillis         当前时间
     * @param activeGraceMillis 活跃保护窗口
     * @return 应淘汰的 key（最久未用优先）；无超限或无候选时为空
     */
    public static List<String> selectEvictions(Collection<Usage> usages, long maxBytes,
                                               long nowMillis, long activeGraceMillis) {
        List<String> victims = new ArrayList<>();
        if (usages == null || usages.isEmpty() || maxBytes <= 0) {
            return victims;
        }
        long total = 0;
        for (Usage usage : usages) {
            total += Math.max(0L, usage.bytes());
        }
        if (total <= maxBytes) {
            return victims;
        }
        List<Usage> candidates = new ArrayList<>();
        for (Usage usage : usages) {
            if (nowMillis - usage.lastUsedMillis() > activeGraceMillis) {
                candidates.add(usage);
            }
        }
        candidates.sort(Comparator.comparingLong(Usage::lastUsedMillis));
        for (Usage candidate : candidates) {
            if (total <= maxBytes) {
                break;
            }
            victims.add(candidate.key());
            total -= Math.max(0L, candidate.bytes());
        }
        return victims;
    }
}
