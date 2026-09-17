package com.mineui.ui.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionThrottleTest {

    @Test
    void firstActionGoesImmediately() {
        AtomicLong now = new AtomicLong(0);
        ActionThrottle throttle = new ActionThrottle(200, now::get);
        assertEquals("a", throttle.submit("a"));
        assertNull(throttle.poll());
    }

    @Test
    void rapidActionsKeepLatestAsPending() {
        AtomicLong now = new AtomicLong(0);
        ActionThrottle throttle = new ActionThrottle(200, now::get);

        assertEquals("a", throttle.submit("a"));
        assertNull(throttle.submit("b"));
        assertNull(throttle.submit("c"));
        assertNull(throttle.poll(), "窗口未到不发");

        now.addAndGet(200);
        assertEquals("c", throttle.poll(), "补发最后一次");
        assertNull(throttle.poll());
    }

    @Test
    void pendingFlushesAfterWindow() {
        AtomicLong now = new AtomicLong(1000);
        ActionThrottle throttle = new ActionThrottle(200, now::get);

        assertEquals("a", throttle.submit("a"));
        assertNull(throttle.submit("b"));
        now.addAndGet(199);
        assertNull(throttle.poll());
        now.addAndGet(1);
        assertEquals("b", throttle.poll());
    }

    @Test
    void emptyActionsIgnored() {
        AtomicLong now = new AtomicLong(0);
        ActionThrottle throttle = new ActionThrottle(200, now::get);
        assertNull(throttle.submit(null));
        assertNull(throttle.submit(""));
        assertNull(throttle.poll());
    }

    @Test
    void rejectsInvalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> new ActionThrottle(0));
    }
}
