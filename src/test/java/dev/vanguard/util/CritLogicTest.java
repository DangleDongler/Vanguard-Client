package dev.vanguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CritLogicTest {
    private static final double PLAYER_GRAVITY = 0.08;

    @Test
    void alreadyFallingCritsNextTick() {
        assertEquals(1, FallTiming.ticksUntilFalling(-0.1, PLAYER_GRAVITY));
    }

    @Test
    void jumpApexMatchesVanillaPhysics() {
        // Simulate the vanilla order directly: move by the speed, then apply gravity and drag.
        double y = 0;
        double v = FallTiming.JUMP_VELOCITY;
        int ticks = 0;
        double fallDistance = 0;
        double lastY = y;
        while (fallDistance <= 0) {
            y += v;
            v = (v - PLAYER_GRAVITY) * 0.98;
            if (y < lastY) fallDistance += lastY - y;
            lastY = y;
            ticks++;
        }
        assertEquals(ticks, FallTiming.ticksUntilFalling(FallTiming.JUMP_VELOCITY, PLAYER_GRAVITY));
    }

    @Test
    void neverFallsWithoutGravity() {
        assertEquals(Integer.MAX_VALUE, FallTiming.ticksUntilFalling(0.2, 0));
    }

    @Test
    void followsSprintUpdatesTheClientSends() {
        ServerSprintTracker tracker = new ServerSprintTracker();
        tracker.observeSent(false);
        assertFalse(tracker.serverSprinting());
        tracker.observeSent(true);
        assertTrue(tracker.serverSprinting());
    }

    @Test
    void sprintHitEntersSprintCritState() {
        ServerSprintTracker tracker = new ServerSprintTracker();
        tracker.observeSent(true);
        tracker.onAttack(1.0f);
        // The client still reports sprinting (nothing new was sent), but the server stopped it.
        tracker.observeSent(true);
        assertFalse(tracker.serverSprinting());
    }

    @Test
    void weakHitsDoNotResetSprint() {
        ServerSprintTracker tracker = new ServerSprintTracker();
        tracker.observeSent(true);
        tracker.onAttack(0.5f);
        assertTrue(tracker.serverSprinting());
    }

    @Test
    void resprintingLeavesSprintCritState() {
        ServerSprintTracker tracker = new ServerSprintTracker();
        tracker.observeSent(true);
        tracker.onAttack(1.0f);
        tracker.observeSent(false);
        tracker.observeSent(true);
        assertTrue(tracker.serverSprinting());
    }
}
