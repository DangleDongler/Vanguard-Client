package dev.vanguard.gui.anim;

/**
 * A value pulled towards a target by a damped spring, for motion that feels physical: it can
 * overshoot a little and settle, and retargeting keeps the current velocity. Steps with the real
 * frame time, in fixed substeps so it behaves the same at any frame rate.
 */
public final class Spring {
    private static final float STEP = 1f / 240f;
    private static final float MAX_FRAME = 1f / 20f;

    private final float stiffness;
    private final float damping;
    private float value;
    private float velocity;
    private float target;
    private long lastNanos = System.nanoTime();

    /**
     * @param stiffness pull towards the target; higher is faster
     * @param damping   resistance; about {@code 2 * sqrt(stiffness)} stops without overshooting
     */
    public Spring(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public float target() {
        return target;
    }

    /** Jumps to {@code value} and stops. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0;
    }

    public float velocity() {
        return velocity;
    }

    /** Advances to now and returns the value. Call once per frame. */
    public float update() {
        long now = System.nanoTime();
        float elapsed = Math.min((now - lastNanos) / 1e9f, MAX_FRAME) * Animation.globalSpeed();
        lastNanos = now;
        while (elapsed > 0) {
            float dt = Math.min(STEP, elapsed);
            float force = -stiffness * (value - target) - damping * velocity;
            velocity += force * dt;
            value += velocity * dt;
            elapsed -= dt;
        }
        if (Math.abs(value - target) < 1e-3f && Math.abs(velocity) < 1e-2f) {
            value = target;
            velocity = 0;
        }
        return value;
    }

    public float get() {
        return value;
    }
}
