package dev.vanguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldsTest {
    private static final double SHIELD_ANGLE = 90;

    @Test
    void coversHitsFromTheFront() {
        // Yaw 0 faces +Z (south); the attacker stands to the south.
        assertTrue(Shields.coversDirection(0f, 0, 3, SHIELD_ANGLE));
        assertTrue(Shields.coversDirection(0f, 2, 2, SHIELD_ANGLE), "45 degrees off to the side is still covered");
    }

    @Test
    void doesNotCoverHitsFromBehind() {
        assertFalse(Shields.coversDirection(0f, 0, -3, SHIELD_ANGLE));
        assertFalse(Shields.coversDirection(0f, 2, -0.1, SHIELD_ANGLE), "just behind the side is open");
    }

    @Test
    void exactlySideOnIsCovered() {
        // The server blocks when the angle is at most 90 degrees, so a perfect side hit is blocked.
        assertTrue(Shields.coversDirection(0f, 3, 0, SHIELD_ANGLE));
        assertTrue(Shields.coversDirection(0f, -3, 0, SHIELD_ANGLE));
    }

    @Test
    void followsTheHoldersYaw() {
        // Yaw 90 faces -X (west).
        assertTrue(Shields.coversDirection(90f, -3, 0, SHIELD_ANGLE));
        assertFalse(Shields.coversDirection(90f, 3, 0.5, SHIELD_ANGLE));
        // Yaw wraps: -270 is the same as 90.
        assertTrue(Shields.coversDirection(-270f, -3, 0, SHIELD_ANGLE));
    }

    @Test
    void narrowShieldsCoverLess() {
        assertTrue(Shields.coversDirection(0f, 1, 3, 30));
        assertFalse(Shields.coversDirection(0f, 3, 1, 30));
    }
}
