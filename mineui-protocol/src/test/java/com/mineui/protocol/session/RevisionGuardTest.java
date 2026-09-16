package com.mineui.protocol.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevisionGuardTest {

    @Test
    void acceptsInOrderSequence() {
        RevisionGuard guard = new RevisionGuard();
        assertEquals(RevisionGuard.Decision.ACCEPT, guard.submit(0));
        assertEquals(1, guard.revision());
        assertEquals(RevisionGuard.Decision.ACCEPT, guard.submit(1));
        assertEquals(2, guard.revision());
    }

    @Test
    void rejectsStaleRevision() {
        RevisionGuard guard = new RevisionGuard(5);
        assertEquals(RevisionGuard.Decision.STALE, guard.submit(4));
        assertEquals(RevisionGuard.Decision.STALE, guard.submit(0));
        assertEquals(5, guard.revision());
    }

    @Test
    void rejectsFutureRevision() {
        RevisionGuard guard = new RevisionGuard(3);
        assertEquals(RevisionGuard.Decision.INVALID, guard.submit(4));
        assertEquals(3, guard.revision());
    }

    @Test
    void doubleSubmitOfSameRevisionOnlyFirstWins() {
        RevisionGuard guard = new RevisionGuard(10);
        assertEquals(RevisionGuard.Decision.ACCEPT, guard.submit(10));
        assertEquals(RevisionGuard.Decision.STALE, guard.submit(10));
    }

    @Test
    void rejectsNegativeInitialRevision() {
        try {
            new RevisionGuard(-1);
            throw new AssertionError("应当抛出异常");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
