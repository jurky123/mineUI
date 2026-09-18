package com.mineui.ui.util;

import com.mineui.protocol.msg.RemoteImagePolicy;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteUrlGuardTest {

    private static final RemoteImagePolicy POLICY =
            new RemoteImagePolicy(true, List.of("imgur.com"), 1024, 1024);

    @Test
    void acceptsHttpsOnAllowlistedDomain() {
        assertEquals(RemoteUrlGuard.Result.OK, RemoteUrlGuard.check("https://i.imgur.com/a.png", POLICY));
    }

    @Test
    void rejectsHttpAndOtherSchemes() {
        assertEquals(RemoteUrlGuard.Result.NOT_HTTPS, RemoteUrlGuard.check("http://i.imgur.com/a.png", POLICY));
        assertEquals(RemoteUrlGuard.Result.NOT_HTTPS, RemoteUrlGuard.check("ftp://i.imgur.com/a.png", POLICY));
    }

    @Test
    void rejectsUnlistedHost() {
        assertEquals(RemoteUrlGuard.Result.HOST_NOT_ALLOWED,
                RemoteUrlGuard.check("https://evil.example/a.png", POLICY));
        assertEquals(RemoteUrlGuard.Result.HOST_NOT_ALLOWED,
                RemoteUrlGuard.check("https://imgur.com.evil.net/a.png", POLICY));
    }

    @Test
    void rejectsDisabledPolicy() {
        assertEquals(RemoteUrlGuard.Result.DISABLED,
                RemoteUrlGuard.check("https://i.imgur.com/a.png", RemoteImagePolicy.disabled()));
        assertEquals(RemoteUrlGuard.Result.DISABLED, RemoteUrlGuard.check("https://i.imgur.com/a.png", null));
    }

    @Test
    void rejectsBadUrl() {
        assertEquals(RemoteUrlGuard.Result.BAD_URL, RemoteUrlGuard.check("not a url", POLICY));
        assertEquals(RemoteUrlGuard.Result.BAD_URL, RemoteUrlGuard.check("https:///nohost", POLICY));
    }

    @Test
    void classifiesPrivateAddresses() throws Exception {
        assertTrue(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("169.254.1.1")));
        assertFalse(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(RemoteUrlGuard.isPrivateAddress(InetAddress.getByName("1.1.1.1")));
    }
}
