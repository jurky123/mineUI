package com.mineui.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerTest {

    @Test
    void comparesNumericSegments() {
        assertTrue(SemVer.compare("0.13.2", "0.14.0") < 0);
        assertTrue(SemVer.compare("0.14.0", "0.13.2") > 0);
        assertEquals(0, SemVer.compare("0.14.0", "0.14.0"));
        assertTrue(SemVer.compare("0.14", "0.14.0") == 0);
        assertTrue(SemVer.compare("1.0", "0.99.99") > 0);
    }

    @Test
    void ignoresSuffixAndBadSegments() {
        assertEquals(0, SemVer.compare("0.14.0-SNAPSHOT", "0.14.0"));
        assertEquals(0, SemVer.compare("dev", "0"));
    }

    @Test
    void isOlder() {
        assertTrue(SemVer.isOlder("0.13.2", "0.14.0"));
        assertFalse(SemVer.isOlder("0.14.0", "0.14.0"));
        assertFalse(SemVer.isOlder("0.14.1", "0.14.0"));
    }
}
