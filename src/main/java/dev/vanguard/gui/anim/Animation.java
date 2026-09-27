package dev.vanguard.gui.anim;

/**
 * A float that eases towards a target over time. Retargeting mid-flight starts a new
 * transition from the current value, so interrupted animations never jump.
 */
public final class Animation {
    private static float globalSpeed = 1f;

    private final float durationMs;
    private final Easing easing;
    private float from;
    private float to;
    private long startNanos;

    public Animation(float initial, float durationMs, Easing easing) {
        this.durationMs = durationMs;
        this.easing = easing;
        this.from = initial;
        this.to = initial;
        this.startNanos = System.nanoTime();
    }

    /** Scales every animation's speed; values above 1 are faster. */
    public static void setGlobalSpeed(float speed) {
        globalSpeed = Math.max(0.01f, speed);
    }

    public void animateTo(float target) {
        if (target == to) return;
        from = get();
        to = target;
        startNanos = System.nanoTime();
    }

    /** Jumps straight to {@code value} without animating. */
    public void snap(float value) {
        from = value;
        to = value;
    }

    public float target() {
        return to;
    }

    public float progress() {
        float elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f;
        return Math.min(1f, elapsedMs * globalSpeed / durationMs);
    }

    public boolean isDone() {
        return progress() >= 1f;
    }

    public float get() {
        float t = progress();
        if (t >= 1f) return to;
        return from + (to - from) * easing.apply(t);
    }
}
