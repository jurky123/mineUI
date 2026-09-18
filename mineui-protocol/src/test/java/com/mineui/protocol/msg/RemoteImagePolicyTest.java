package com.mineui.protocol.msg;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteImagePolicyTest {

    private static RemoteImagePolicy policy(String... domains) {
        return new RemoteImagePolicy(true, List.of(domains), 1024, 1024);
    }

    @Test
    void allowsExactDomain() {
        assertTrue(policy("i.imgur.com").allowsHost("i.imgur.com"));
    }

    @Test
    void allowsSubdomain() {
        assertTrue(policy("imgur.com").allowsHost("i.imgur.com"));
        assertTrue(policy("imgur.com").allowsHost("cdn.eu.imgur.com"));
    }

    @Test
    void ignoresCase() {
        assertTrue(policy("Imgur.COM").allowsHost("i.IMGUR.com"));
    }

    @Test
    void rejectsUnlistedAndPartialMatches() {
        assertFalse(policy("imgur.com").allowsHost("evilimgur.com"));
        assertFalse(policy("imgur.com").allowsHost("imgur.com.evil.net"));
        assertFalse(policy("imgur.com").allowsHost("example.com"));
        assertFalse(policy("imgur.com").allowsHost(null));
        assertFalse(policy("imgur.com").allowsHost(" "));
    }

    @Test
    void disabledPolicyRejectsEverything() {
        RemoteImagePolicy disabled = RemoteImagePolicy.disabled();
        assertFalse(disabled.enabled());
        assertFalse(disabled.allowsHost("i.imgur.com"));
    }

    @Test
    void normalizesLimits() {
        RemoteImagePolicy normalized = new RemoteImagePolicy(true, null, 0, 0);
        assertTrue(normalized.maxBytes() > 0);
        assertTrue(normalized.cacheBytes() > 0);
        assertTrue(normalized.allowedDomains().isEmpty());
    }
}
