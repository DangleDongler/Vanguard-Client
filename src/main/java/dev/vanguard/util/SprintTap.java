package dev.vanguard.util;

/**
 * Plans sprint resets one movement tick at a time: when to let go of forward, how long to step back,
 * and when to sprint again. Pure logic, fed a {@link Situation} each tick, so it can be tested
 * without the game.
 *
 * <p>How a reset works in the game: sprinting stops on any tick where you aren't moving forward, and
 * the client then tells the server. On the next tick, forward plus the sprint key starts it again,
 * and the client tells the server that too. So the shortest reset is one tick off forward and one
 * tick back on, and a hit sent after that is a sprint hit.
 *
 * <p><b>W-tap</b> takes that one tick as late as it safely can: {@link #LEAD_TICKS} before your
 * weapon is ready. Until then you keep the sprint-crit state (your falling hits can still crit), and
 * the choice to reset is made with the freshest information. Letting go of forward late also costs
 * less ground at the moment of the hit than doing it right away.
 *
 * <p><b>S-tap</b> steps back right after the hit instead, while the opponent is close enough to hit
 * you back, and sprints in again as your weapon becomes ready.
 */
public final class SprintTap {
    /** What to do with the movement keys this tick. */
    public enum Action {
        /** Leave them alone. */
        NONE,
        /** Let go of forward (a W-tap). */
        RELEASE,
        /** Let go of forward and hold back (an S-tap). */
        BACK_OFF,
        /** Forward again, with the sprint key, so the sprint starts again. */
        RESUME
    }

    /**
     * Start a W-tap when the weapon is this many ticks from ready. The reset takes two ticks (off,
     * then on), so this leaves one spare.
     */
    public static final int LEAD_TICKS = 2;
    /** Longest an S-tap steps back. */
    public static final int MAX_BACK_OFF_TICKS = 10;
    /** Longest it keeps trying to start the sprint again, e.g. while something blocks it. */
    public static final int MAX_RESUME_TICKS = 10;

    /**
     * Everything the plan depends on, as of this tick's movement.
     *
     * @param forwardHeld     you're holding forward (and not back) yourself
     * @param sprinting       the client is sprinting right now
     * @param needsReset      the client sprints but the server thinks you don't (the sprint-crit state)
     * @param resetAllowed    now is a good moment: a fight is on, no crit is coming, it's not a jump tick
     * @param sTap            S-tap rather than W-tap
     * @param tooClose        the opponent is close enough to hit you
     * @param safeBehind      stepping back won't walk you off a ledge or into lava
     * @param ticksUntilReady ticks until your weapon can land a sprint hit
     */
    public record Situation(boolean forwardHeld, boolean sprinting, boolean needsReset, boolean resetAllowed,
                            boolean sTap, boolean tooClose, boolean safeBehind, int ticksUntilReady) {
    }

    private int releasedTicks;
    private boolean backingOff;
    private int resumeTicks;

    /** The action for this tick. Call exactly once per movement tick. */
    public Action next(Situation s) {
        if (!s.forwardHeld()) {
            // You let go yourself: there's nothing to reset or resume.
            cancel();
            return Action.NONE;
        }

        if (releasedTicks > 0) {
            if (backingOff && shouldBackOff(s) && releasedTicks < MAX_BACK_OFF_TICKS) {
                releasedTicks++;
                return Action.BACK_OFF;
            }
            releasedTicks = 0;
            backingOff = false;
            resumeTicks = 1;
            return Action.RESUME;
        }

        if (resumeTicks > 0) {
            if (s.sprinting() || resumeTicks >= MAX_RESUME_TICKS) {
                resumeTicks = 0;
                return Action.NONE;
            }
            resumeTicks++;
            return Action.RESUME;
        }

        if (!s.needsReset() || !s.resetAllowed()) return Action.NONE;
        if (s.sTap() && shouldBackOff(s)) {
            releasedTicks = 1;
            backingOff = true;
            return Action.BACK_OFF;
        }
        if (s.ticksUntilReady() <= LEAD_TICKS) {
            releasedTicks = 1;
            backingOff = false;
            return Action.RELEASE;
        }
        return Action.NONE;
    }

    /** Stepping back is worth it while they can reach you and your weapon is still charging. */
    private static boolean shouldBackOff(Situation s) {
        return s.tooClose() && s.safeBehind() && s.ticksUntilReady() > LEAD_TICKS;
    }

    /** A reset is under way: forward is off, or the sprint hasn't started again yet. */
    public boolean busy() {
        return releasedTicks > 0 || resumeTicks > 0;
    }

    public void cancel() {
        releasedTicks = 0;
        backingOff = false;
        resumeTicks = 0;
    }

    /**
     * Ticks until a hit sent after that tick is charged enough for a sprint hit (over 90%), counted
     * from this tick's movement.
     *
     * <p>The charge counter goes up once per tick, after movement, and the game reads it with half a
     * tick added. So a hit sent after {@code k} more ticks sees {@code (ticker + 1 + k + 0.5) / delay}.
     *
     * @param ticker ticks since your last attack (or item switch), as of this tick's movement
     * @param delay  ticks your held item takes to charge fully
     */
    public static int ticksUntilReady(float ticker, float delay) {
        double needed = ServerSprintTracker.SPRINT_HIT_CHARGE * delay - ticker - 1.5;
        if (needed < 0) return 0;
        return (int) Math.floor(needed) + 1;
    }
}
