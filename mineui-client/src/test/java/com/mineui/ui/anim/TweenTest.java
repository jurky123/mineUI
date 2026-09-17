package com.mineui.ui.anim;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TweenTest {

    @Test
    void respectsDelay() {
        Tween tween = new Tween(0f, 10f, 1f, 0.5f, Easing.LINEAR);
        tween.update(0.25f);
        assertEquals(0f, tween.value(), 0.0001f);
        assertFalse(tween.done());
    }

    @Test
    void interpolatesLinearly() {
        Tween tween = new Tween(10f, 20f, 1f, Easing.LINEAR);
        tween.update(0.5f);
        assertEquals(15f, tween.value(), 0.0001f);
    }

    @Test
    void reachesTargetAndCompletes() {
        Tween tween = new Tween(0f, 5f, 0.2f, Easing.EASE_OUT);
        tween.update(0.5f);
        assertEquals(5f, tween.value(), 0.0001f);
        assertTrue(tween.done());
    }

    @Test
    void onCompleteCalledOnce() {
        AtomicInteger calls = new AtomicInteger();
        Tween tween = new Tween(0f, 1f, 0.1f, Easing.LINEAR).onComplete(calls::incrementAndGet);
        tween.update(0.05f);
        assertEquals(0, calls.get());
        tween.update(0.05f);
        assertEquals(1, calls.get());
        tween.update(1f);
        assertEquals(1, calls.get());
    }

    @Test
    void zeroDurationJumpsAfterDelay() {
        Tween tween = new Tween(0f, 7f, 0f, 0.1f, Easing.LINEAR);
        tween.update(0.05f);
        assertEquals(0f, tween.value(), 0.0001f);
        tween.update(0.06f);
        assertEquals(7f, tween.value(), 0.0001f);
        assertTrue(tween.done());
    }

    @Test
    void negativeDeltaIgnored() {
        Tween tween = new Tween(0f, 1f, 1f, Easing.LINEAR);
        tween.update(-5f);
        assertEquals(0f, tween.value(), 0.0001f);
    }
}
