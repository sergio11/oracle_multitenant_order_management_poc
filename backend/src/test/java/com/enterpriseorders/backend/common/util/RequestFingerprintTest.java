package com.enterpriseorders.backend.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestFingerprintTest {

    @Test
    void samePartsProduceSameHexDigest() {
        String first = RequestFingerprint.of("PAYMENT", 1L, "PAY-1");
        String retry = RequestFingerprint.of("PAYMENT", 1L, "PAY-1");

        assertEquals(first, retry);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void unitSeparatorPreventsAdjacentPartCollisions() {
        String mergedLeft = RequestFingerprint.of("AB", "C");
        String mergedRight = RequestFingerprint.of("A", "BC");

        assertNotEquals(mergedLeft, mergedRight);
    }

    @Test
    void nullPartIsTreatedAsEmptyAndDistinguishedFromMissingPart() {
        String withNull = RequestFingerprint.of("PAYMENT", null);
        String empty = RequestFingerprint.of("PAYMENT", "");

        assertEquals(empty, withNull);
        assertNotEquals(RequestFingerprint.of("PAYMENT"), withNull);
    }

    @Test
    void differentParamsProduceDifferentFingerprints() {
        String original = RequestFingerprint.of("PAYMENT", 1L, "PAY-1", "100.00", "USD", "CARD");
        String changedAmount = RequestFingerprint.of("PAYMENT", 1L, "PAY-1", "999.99", "USD", "CARD");

        assertNotEquals(original, changedAmount);
    }

    @Test
    void isStableAcrossRunsForFixedInput() {
        // Pinned SHA-256 over ("CREATE_ORDER" 0x1F "100" 0x1F "USD" 0x1F):
        // a hashing change could silently invalidate persisted V024 rows, so
        // the digest itself is asserted, not just self-consistency.
        assertEquals(
                "1a084841635389eda3499a77ab7c4a44d058154f3d87edf3b734d334d512aad7",
                RequestFingerprint.of("CREATE_ORDER", 100L, "USD"));
    }
}
