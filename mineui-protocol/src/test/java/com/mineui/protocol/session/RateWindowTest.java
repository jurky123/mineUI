package com.mineui.protocol.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateWindowTest {

    @Test
    void allowsUpToLimit() {
        AtomicLong now = new AtomicLong(1000);
        RateWindow window = new RateWindow(3, 1000, now::get);

        assertTrue(window.tryAcquire());
        assertTrue(window.tryAcquire());
        assertTrue(window.tryAcquire());
        assertFalse(window.tryAcquire());
    }

    @Test
    void resetsAfterWindow() {
        AtomicLong now = new AtomicLong(0);
        RateWindow window = new RateWindow(2, 1000, now::get);

        assertTrue(window.tryAcquire());
        assertTrue(window.tryAcquire());
        assertFalse(window.tryAcquire());

        now.addAndGet(1000);
        assertTrue(window.tryAcquire());
        assertTrue(window.tryAcquire());
        assertFalse(window.tryAcquire());
    }

    @Test
    void doesNotResetBeforeWindow() {
        AtomicLong now = new AtomicLong(0);
        RateWindow window = new RateWindow(1, 1000, now::get);

        assertTrue(window.tryAcquire());
        now.addAndGet(999);
        assertFalse(window.tryAcquire());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new RateWindow(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new RateWindow(1, 0));
    }
}
