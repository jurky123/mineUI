package com.mineui.ui.util;

import com.mineui.protocol.msg.RemoteImagePolicy;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 远程图片 URL 校验（纯逻辑，便于单测）：
 * 仅 HTTPS、域名必须在服务端策略白名单内、拒绝私网/回环/链路本地地址。
 */
public final class RemoteUrlGuard {

    public enum Result {
        OK,
        DISABLED,
        NOT_HTTPS,
        HOST_NOT_ALLOWED,
        PRIVATE_ADDRESS,
        BAD_URL
    }

    private RemoteUrlGuard() {
    }

    /** URL 层面校验（不做 DNS 解析）。 */
    public static Result check(String url, RemoteImagePolicy policy) {
        if (policy == null || !policy.enabled()) {
            return Result.DISABLED;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Result.BAD_URL;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return Result.NOT_HTTPS;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Result.BAD_URL;
        }
        if (!policy.allowsHost(host)) {
            return Result.HOST_NOT_ALLOWED;
        }
        return Result.OK;
    }

    /** 已解析地址校验：私网/回环/链路本地/任意本地/组播一律拒绝。 */
    public static boolean isPrivateAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }
}
