package dev.vanguard.util;

import dev.vanguard.util.ServerSprintTracker.HitSound;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSprintTrackerTest {
    private static final int ANSWER = 6;

    private static ServerSprintTracker sprinting() {
        ServerSprintTracker tracker = new ServerSprintTracker();
        tracker.observeSent(true);
        return tracker;
    }

    private static void sprintHit(ServerSprintTracker tracker) {
        tracker.onAttack(1.0f, Vec3.ZERO, ANSWER);
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
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        // The client still reports sprinting (nothing new was sent), but the server stopped it.
        tracker.observeSent(true);
        assertFalse(tracker.serverSprinting());
    }

    @Test
    void weakHitsDoNotStopTheSprint() {
        ServerSprintTracker tracker = sprinting();
        tracker.onAttack(0.5f, Vec3.ZERO, ANSWER);
        assertTrue(tracker.serverSprinting());
    }

    @Test
    void aSprintResetLeavesTheSprintCritState() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        tracker.observeSent(false);
        tracker.observeSent(true);
        assertTrue(tracker.serverSprinting());
    }

    @Test
    void aSprintHitThatDidNotLandKeepsTheSprint() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        tracker.onOwnHitSound(HitSound.KNOCKBACK);
        tracker.onOwnHitSound(HitSound.NO_DAMAGE);
        assertTrue(tracker.serverSprinting(), "blocked or during immunity: the server never stopped the sprint");
    }

    @Test
    void aSprintHitThatLandedStopsTheSprint() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        tracker.onOwnHitSound(HitSound.KNOCKBACK);
        tracker.onOwnHitSound(HitSound.LANDED);
        tracker.onOwnHitSound(HitSound.NO_DAMAGE);
        assertFalse(tracker.serverSprinting(), "only the first verdict counts");
    }

    @Test
    void aMissWithoutTheKnockbackSoundWasNoSprintHit() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        tracker.onOwnHitSound(HitSound.NO_DAMAGE);
        assertFalse(tracker.serverSprinting(), "no knockback sound: the server didn't see us sprinting");
    }

    @Test
    void aVerdictAfterWeChangedOurSprintIsOutOfDate() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        tracker.observeSent(false); // a reset began before the answer came back
        tracker.onOwnHitSound(HitSound.KNOCKBACK);
        tracker.onOwnHitSound(HitSound.NO_DAMAGE);
        assertFalse(tracker.serverSprinting(), "our stop was handled after the hit");
    }

    @Test
    void lateVerdictsAreIgnored() {
        ServerSprintTracker tracker = sprinting();
        sprintHit(tracker);
        for (int i = 0; i <= ANSWER; i++) tracker.tick();
        tracker.onOwnHitSound(HitSound.KNOCKBACK);
        tracker.onOwnHitSound(HitSound.NO_DAMAGE);
        assertFalse(tracker.serverSprinting());
    }

    @Test
    void forgetsEverythingForANewPlayer() {
        ServerSprintTracker tracker = sprinting();
        tracker.onPlayer(new Object());
        assertFalse(tracker.serverSprinting());
        tracker.observeSent(true);
        assertTrue(tracker.serverSprinting());
    }
}
