package dev.vanguard.util;

import dev.vanguard.util.SwapSchedule.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapScheduleTest {
    private static final int BOT = 7;

    @Test
    void startsIdle() {
        SwapSchedule schedule = new SwapSchedule();
        assertEquals(Phase.IDLE, schedule.phase());
        assertFalse(schedule.hitDue(100, 0));
        assertFalse(schedule.swapBackDue(100, 1));
    }

    @Test
    void countsTheSwapDelayFromWhenTheSwitchWasSent() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        assertEquals(BOT, schedule.targetId());
        assertFalse(schedule.hitDue(105, 2), "the server hasn't seen the axe yet");
        schedule.sent(101);
        assertTrue(schedule.isSent());
        assertFalse(schedule.hitDue(101, 2));
        assertFalse(schedule.hitDue(102, 2));
        assertTrue(schedule.hitDue(103, 2));
    }

    @Test
    void onlyTheFirstSendCounts() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        schedule.sent(101);
        schedule.sent(104);
        assertTrue(schedule.hitDue(102, 1));
    }

    @Test
    void switchesBackAfterTheSwapBackDelay() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        schedule.sent(101);
        schedule.hit(101);
        assertEquals(Phase.HIT, schedule.phase());
        assertFalse(schedule.hitDue(105, 0), "one hit per swap");
        // Made at the end of tick 102, the switch goes out at the start of 103: 2 ticks after the hit.
        assertFalse(schedule.swapBackDue(101, 2));
        assertTrue(schedule.swapBackDue(102, 2));
    }

    @Test
    void givesUpIfTheTargetNeverComesUnderTheCrosshair() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        assertFalse(schedule.timedOut(101 + SwapSchedule.PENDING_TIMEOUT_TICKS, 1));
        assertTrue(schedule.timedOut(102 + SwapSchedule.PENDING_TIMEOUT_TICKS, 1));
    }

    @Test
    void timeoutOnlyWhileWaitingToHit() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        schedule.hit(101);
        assertFalse(schedule.timedOut(1000, 1));
    }

    @Test
    void clearGoesBackToIdle() {
        SwapSchedule schedule = new SwapSchedule();
        schedule.swapped(BOT, 100);
        schedule.clear();
        assertEquals(Phase.IDLE, schedule.phase());
        assertEquals(-1, schedule.targetId());
    }
}
