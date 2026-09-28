package dev.vanguard.util;

/** Predicts when a player in the air starts falling, which is when their hits start to crit. */
public final class FallTiming {
    /** Air drag applied to vertical speed every tick. */
    private static final double VERTICAL_DRAG = 0.98;
    /** Upward speed a normal jump starts with. */
    public static final double JUMP_VELOCITY = 0.42;

    private FallTiming() {
    }

    /**
     * Ticks until the player's fall distance becomes positive, given their current vertical speed
     * (blocks per tick, as in {@code getDeltaMovement().y}) and gravity.
     *
     * <p>Each tick the player first moves by their speed, then gravity and drag update it. So the
     * player keeps rising while the speed is positive, and the first tick that moves them down is
     * the one after the speed turns negative. Returns {@code Integer.MAX_VALUE} when they'd never fall.
     */
    public static int ticksUntilFalling(double velocityY, double gravity) {
        if (velocityY < 0) return 1;
        if (gravity <= 0) return Integer.MAX_VALUE;
        int ticks = 0;
        double v = velocityY;
        while (v >= 0) {
            v = (v - gravity) * VERTICAL_DRAG;
            ticks++;
            if (ticks > 200) return Integer.MAX_VALUE;
        }
        return ticks + 1;
    }
}
