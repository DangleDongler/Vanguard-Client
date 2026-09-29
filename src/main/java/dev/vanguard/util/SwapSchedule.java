package dev.vanguard.util;

/**
 * The timing of one axe swap for a shield break: switch to the axe, wait, hit, wait, switch back.
 * Counted in client ticks. Pure logic, so it can be tested without the game.
 */
public final class SwapSchedule {
    public enum Phase {
        /** Not holding an axe for a break. */
        IDLE,
        /** Switched to the axe, waiting to hit. */
        SWAPPED,
        /** Hit with the axe, waiting to switch back. */
        HIT
    }

    /** How long a switched-to axe waits for the target to be under the crosshair before giving up. */
    public static final int PENDING_TIMEOUT_TICKS = 10;

    private Phase phase = Phase.IDLE;
    private int targetId = -1;
    private int swapTick;
    private int sentTick = -1;
    private int hitTick;

    public Phase phase() {
        return phase;
    }

    /** The player whose shield the pending hit is for. */
    public int targetId() {
        return targetId;
    }

    /** Switched to the axe at {@code tick} to break {@code targetId}'s shield. */
    public void swapped(int targetId, int tick) {
        this.targetId = targetId;
        swapTick = tick;
        sentTick = -1;
        phase = Phase.SWAPPED;
    }

    /**
     * The switch reached the server at {@code tick}. The game sends it at the start of the next
     * tick, so the swap delay counts from here rather than from when the axe was picked.
     */
    public void sent(int tick) {
        if (phase == Phase.SWAPPED && sentTick < 0) sentTick = tick;
    }

    public boolean isSent() {
        return sentTick >= 0;
    }

    /** The axe hit went out at {@code tick}. */
    public void hit(int tick) {
        hitTick = tick;
        phase = Phase.HIT;
    }

    /** The server has seen the axe in your hand for the swap delay, so it may hit now. */
    public boolean hitDue(int tick, int swapDelay) {
        return phase == Phase.SWAPPED && sentTick >= 0 && tick - sentTick >= swapDelay;
    }

    /** Switched to the axe but couldn't hit in time: the target left the crosshair. */
    public boolean timedOut(int tick, int swapDelay) {
        return phase == Phase.SWAPPED && tick - swapTick > swapDelay + PENDING_TIMEOUT_TICKS;
    }

    /**
     * Time to switch back, so that the switch reaches the server {@code swapBackDelay} ticks after the
     * hit. Called at the end of each tick; a switch made then goes out at the start of the next one,
     * so it's made a tick early.
     */
    public boolean swapBackDue(int tick, int swapBackDelay) {
        return phase == Phase.HIT && tick - hitTick >= swapBackDelay - 1;
    }

    public void clear() {
        phase = Phase.IDLE;
        targetId = -1;
        sentTick = -1;
    }
}
