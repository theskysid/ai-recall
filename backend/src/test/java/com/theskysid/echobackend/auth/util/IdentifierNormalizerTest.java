package com.theskysid.echobackend.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifierNormalizerTest {

    @Test
    void hasWhitespaceDetectsInternalAndEdgeSpaces() {
        assertTrue(IdentifierNormalizer.hasWhitespace("alex kumar"));
        assertTrue(IdentifierNormalizer.hasWhitespace("alex\tkumar"));
        assertTrue(IdentifierNormalizer.hasWhitespace(" alex"));
        assertFalse(IdentifierNormalizer.hasWhitespace("alex_kumar"));
        assertFalse(IdentifierNormalizer.hasWhitespace(""));
        assertFalse(IdentifierNormalizer.hasWhitespace(null));
    }

    @Test
    void stripWhitespaceCollapsesEverything() {
        assertEquals("alexkumar", IdentifierNormalizer.stripWhitespace("alex kumar"));
        assertEquals("alexkumar", IdentifierNormalizer.stripWhitespace("  alex   kumar  "));
        assertEquals("", IdentifierNormalizer.stripWhitespace(null));
    }

    @Test
    void emailLocalPartIsAlwaysWhitespaceFree() {
        // the username Google signups are derived from
        String local = IdentifierNormalizer.normalizeEmail("  Alex.Kumar@Gmail.com ").split("@")[0];
        assertEquals("alex.kumar", local);
        assertFalse(IdentifierNormalizer.hasWhitespace(local));
    }
}
