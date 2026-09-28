package dev.vanguard.util;

import dev.vanguard.util.SprintTap.Action;
import dev.vanguard.util.SprintTap.Situation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SprintTapTest {
    /** Sword: 1.6 attacks per second. */
    private static final float SWORD = 12.5f;

    /** Running at the target in the sprint-crit state, weapon {@code ready} ticks from charged. */
    private static Situation needsReset(int ready) {
        return new Situation(true, true, true, true, false, false, false, ready);
    }

    /** What the game does after each action: sprinting stops without forward, restarts with it. */
    private static Situation after(Action action, Situation s) {
        boolean sprinting = switch (action) {
            case RELEASE, BACK_OFF -> false;
            case RESUME -> true;
            case NONE -> s.sprinting();
        };
        boolean needsReset = s.needsReset() && action == Action.NONE;
        return new Situation(s.forwardHeld(), sprinting, needsReset, s.resetAllowed(), s.sTap(), s.tooClose(),
            s.safeBehind(), Math.max(0, s.ticksUntilReady() - 1));
    }

    @Test
    void chargeCountsTheWayTheGameReadsIt() {
        // Right after a hit the counter is 0. A hit sent after the 10th tick sees (0 + 1 + 10 + 0.5) / 12.5 = 0.92.
        assertEquals(10, SprintTap.ticksUntilReady(0, SWORD));
        // After the 9th it would see 0.84: not a sprint hit yet.
        assertTrue((0 + 1 + 9 + 0.5f) / SWORD <= 0.9f);
        assertEquals(0, SprintTap.ticksUntilReady(SWORD, SWORD));
        assertEquals(1, SprintTap.ticksUntilReady(9, SWORD));
    }

    @Test
    void wTapWaitsUntilJustBeforeTheWeaponIsReady() {
        SprintTap tap = new SprintTap();
        for (int ready = 10; ready > SprintTap.LEAD_TICKS; ready--) {
            assertEquals(Action.NONE, tap.next(needsReset(ready)), "keeps the sprint-crit state while charging");
        }
        assertEquals(Action.RELEASE, tap.next(needsReset(SprintTap.LEAD_TICKS)));
        assertTrue(tap.busy());
    }

    @Test
    void wTapIsOneTickOffThenSprintAgain() {
        SprintTap tap = new SprintTap();
        Situation s = needsReset(SprintTap.LEAD_TICKS);
        Action first = tap.next(s);
        assertEquals(Action.RELEASE, first);
        s = after(first, s);
        Action second = tap.next(s);
        assertEquals(Action.RESUME, second);
        s = after(second, s);
        assertEquals(Action.NONE, tap.next(s));
        assertFalse(tap.busy());
        // The reset finished with a tick to spare before the weapon was ready.
        assertTrue(s.ticksUntilReady() >= 0);
    }

    @Test
    void nothingWhenTheServerAlreadyThinksYouSprint() {
        SprintTap tap = new SprintTap();
        assertEquals(Action.NONE, tap.next(new Situation(true, true, false, true, false, false, false, 0)));
    }

    @Test
    void nothingWhenNowIsNotAGoodMoment() {
        SprintTap tap = new SprintTap();
        assertEquals(Action.NONE, tap.next(new Situation(true, true, true, false, false, false, false, 0)));
    }

    @Test
    void lettingGoOfForwardYourselfCancelsIt() {
        SprintTap tap = new SprintTap();
        tap.next(needsReset(0));
        assertEquals(Action.NONE, tap.next(new Situation(false, false, false, true, false, false, false, 0)));
        assertFalse(tap.busy(), "you stopped, so there's no sprint to bring back");
    }

    @Test
    void keepsPressingSprintUntilItStarts() {
        SprintTap tap = new SprintTap();
        tap.next(needsReset(0));
        assertEquals(Action.RESUME, tap.next(new Situation(true, false, false, true, false, false, false, 0)));
        // Something blocked it this tick (say, you started eating): keep trying, but not forever.
        int resumes = 1;
        while (tap.next(new Situation(true, false, false, true, false, false, false, 0)) == Action.RESUME) resumes++;
        assertEquals(SprintTap.MAX_RESUME_TICKS, resumes);
    }

    @Test
    void sTapStepsBackWhileTheyCanReachYou() {
        SprintTap tap = new SprintTap();
        Situation close = new Situation(true, true, true, true, true, true, true, 10);
        assertEquals(Action.BACK_OFF, tap.next(close), "right after the hit, not at the last moment");
        Situation stillClose = new Situation(true, false, false, true, true, true, true, 9);
        assertEquals(Action.BACK_OFF, tap.next(stillClose));
        Situation outOfReach = new Situation(true, false, false, true, true, false, true, 8);
        assertEquals(Action.RESUME, tap.next(outOfReach));
    }

    @Test
    void sTapSprintsBackInAsTheWeaponGetsReady() {
        SprintTap tap = new SprintTap();
        assertEquals(Action.BACK_OFF, tap.next(new Situation(true, true, true, true, true, true, true, 4)));
        assertEquals(Action.BACK_OFF, tap.next(new Situation(true, false, false, true, true, true, true, 3)));
        assertEquals(Action.RESUME, tap.next(new Situation(true, false, false, true, true, true, true, SprintTap.LEAD_TICKS)));
    }

    @Test
    void sTapNeverStepsBackMoreThanTheLimit() {
        SprintTap tap = new SprintTap();
        int backs = 0;
        Action action = tap.next(new Situation(true, true, true, true, true, true, true, 40));
        while (action == Action.BACK_OFF) {
            backs++;
            action = tap.next(new Situation(true, false, false, true, true, true, true, 40));
        }
        assertEquals(SprintTap.MAX_BACK_OFF_TICKS, backs);
        assertEquals(Action.RESUME, action);
    }

    @Test
    void sTapWithNothingSafeBehindFallsBackToAWTap() {
        SprintTap tap = new SprintTap();
        assertEquals(Action.NONE, tap.next(new Situation(true, true, true, true, true, true, false, 10)));
        assertEquals(Action.RELEASE, tap.next(new Situation(true, true, true, true, true, true, false, SprintTap.LEAD_TICKS)));
    }

    @Test
    void sTapWhenTheyAreAlreadyOutOfReachIsAWTap() {
        SprintTap tap = new SprintTap();
        assertEquals(Action.NONE, tap.next(new Situation(true, true, true, true, true, false, true, 10)));
        assertEquals(Action.RELEASE, tap.next(new Situation(true, true, true, true, true, false, true, 1)));
    }
}
