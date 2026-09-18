package com.mineui.protocol.msg;

import java.util.List;
import java.util.Locale;

/**
 * 远程图片策略（服务端权威，客户端强制执行）。
 *
 * @param enabled        是否允许远程图片
 * @param allowedDomains 允许的域名（精确或子域匹配，忽略大小写）
 * @param maxBytes       单张图片大小上限
 * @param cacheBytes     客户端磁盘缓存上限（LRU）
 */
public record RemoteImagePolicy(boolean enabled, List<String> allowedDomains, long maxBytes, long cacheBytes) {

    public RemoteImagePolicy {
        allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
        maxBytes = maxBytes <= 0 ? 1024 * 1024 : maxBytes;
        cacheBytes = cacheBytes <= 0 ? 64L * 1024 * 1024 : cacheBytes;
    }

    public static RemoteImagePolicy disabled() {
        return new RemoteImagePolicy(false, List.of(), 1024 * 1024, 64L * 1024 * 1024);
    }

    /** 域名是否在允许列表内（精确或子域；忽略大小写）。 */
    public boolean allowsHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String domain : allowedDomains) {
            String candidate = domain.toLowerCase(Locale.ROOT);
            if (normalized.equals(candidate) || normalized.endsWith("." + candidate)) {
                return true;
            }
        }
        return false;
    }
}
