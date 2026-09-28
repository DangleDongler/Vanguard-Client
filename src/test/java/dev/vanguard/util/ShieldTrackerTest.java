package dev.vanguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldTrackerTest {
    private static final int BOT = 42;
    private static final int ANSWER = 6;

    private static void advance(ShieldTracker tracker, int ticks) {
        for (int i = 0; i < ticks; i++) tracker.tick();
    }

    @Test
    void unknownShieldsAreNotDown() {
        ShieldTracker tracker = new ShieldTracker();
        assertFalse(tracker.isDownOrBreaking(BOT, ANSWER));
    }

    @Test
    void aSentBreakCountsUntilTheAnswerIsDue() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        assertTrue(tracker.isBreaking(BOT, ANSWER), "don't swing the axe again while the answer is on its way");
        assertFalse(tracker.isDown(BOT));
    }

    @Test
    void confirmationKeepsItDownForTheCooldown() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        tracker.onBreakConfirmed(BOT, 100);
        assertTrue(tracker.isDown(BOT));
        assertFalse(tracker.isBreaking(BOT, ANSWER), "confirmed, no longer waiting");
    }

    @Test
    void aBlockWhileOurBreakIsOnItsWayIsSomeoneElsesHit() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        tracker.onBlocked(BOT, ANSWER);
        assertTrue(tracker.isBreaking(BOT, ANSWER), "an axe hit that's blocked always breaks: don't swing it again");
    }

    @Test
    void aBlockAfterAConfirmedBreakMeansTheShieldIsUpAgain() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        tracker.onBreakConfirmed(BOT, 100);
        tracker.onBlocked(BOT, ANSWER);
        assertFalse(tracker.isDown(BOT));
    }

    @Test
    void aBlockWithNoBreakPendingClearsNothingItShouldnt() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBlocked(BOT, ANSWER);
        assertFalse(tracker.isDownOrBreaking(BOT, ANSWER));
    }

    @Test
    void anUnansweredBreakCanBeRetriedOnceTheAnswerIsOverdue() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        advance(tracker, ANSWER);
        assertTrue(tracker.isBreaking(BOT, ANSWER));
        advance(tracker, 1);
        assertFalse(tracker.isBreaking(BOT, ANSWER), "no answer in time: allow one more try");
    }

    @Test
    void theCooldownRunsOut() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakConfirmed(BOT, 100);
        advance(tracker, 99);
        assertTrue(tracker.isDown(BOT));
        advance(tracker, 1);
        assertFalse(tracker.isDown(BOT), "they can raise it again");
    }

    @Test
    void changingWorldsForgetsEverything() {
        ShieldTracker tracker = new ShieldTracker();
        Object first = new Object();
        tracker.onLevel(first);
        tracker.onBreakConfirmed(BOT, 100);
        tracker.onLevel(first);
        assertTrue(tracker.isDown(BOT));
        tracker.onLevel(new Object());
        assertFalse(tracker.isDown(BOT));
    }

    @Test
    void givesUpWhenTheServerKeepsIgnoringBreaks() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakSent(BOT);
        advance(tracker, ANSWER + 1);
        tracker.onBreakSent(BOT);
        assertFalse(tracker.isGivenUp(BOT), "one unanswered retry is fine");
        advance(tracker, ANSWER + 1);
        tracker.onBreakSent(BOT);
        assertTrue(tracker.isGivenUp(BOT), "two ignored in a row: stop swinging the axe");

        tracker.onNotBlocking(BOT);
        assertFalse(tracker.isGivenUp(BOT), "a fresh raise gets a fresh try");
    }

    @Test
    void answeredBreaksNeverCountAsIgnored() {
        ShieldTracker tracker = new ShieldTracker();
        for (int i = 0; i < 5; i++) {
            tracker.onBreakSent(BOT);
            tracker.onBreakConfirmed(BOT, 100);
            advance(tracker, 100);
        }
        assertFalse(tracker.isGivenUp(BOT));
    }

    @Test
    void otherPlayersAreTrackedSeparately() {
        ShieldTracker tracker = new ShieldTracker();
        tracker.onBreakConfirmed(BOT, 100);
        assertFalse(tracker.isDownOrBreaking(BOT + 1, ANSWER));
    }
}
