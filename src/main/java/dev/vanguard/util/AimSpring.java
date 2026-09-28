package dev.vanguard.util;

/**
 * Moves one aim axis (yaw or pitch, in degrees) toward a goal using a critically damped spring.
 *
 * <p>Why a spring: easing that closes a fixed fraction of the gap each frame starts moving at full
 * speed the instant a target appears, which reads as a jolt. A critically damped spring starts from
 * the current turn speed and accelerates smoothly, then settles without overshooting. The step is
 * solved exactly rather than integrated, so the motion is identical at any frame rate and can't
 * blow up on a long frame.
 *
 * <p>Moving goals: {@code goalRate} is how fast the goal itself is turning (a strafing target).
 * The spring works in a frame that moves along with {@code follow * goalRate}, so at
 * {@code follow = 1} it tracks a steadily moving goal with no lag.
 *
 * <p>The spring only remembers its own turn speed. The remaining error is measured fresh each frame,
 * so the player's own mouse movement is never fought or undone by stale state.
 */
public final class AimSpring {
    private double velocity;

    /**
     * Advances the spring by {@code dt} seconds.
     *
     * @param error    goal minus current angle, in degrees
     * @param goalRate how fast the goal is moving, in degrees per second
     * @param follow   how much of the goal's motion to follow directly, 0 to 1
     * @param omega    stiffness in radians per second; higher settles faster
     * @param dt       frame time in seconds
     * @return how many degrees to turn this frame
     */
    public double step(double error, double goalRate, double follow, double omega, double dt) {
        if (dt <= 0) return 0;
        double carried = follow * goalRate;
        // Offset from the goal and speed relative to it, in the frame moving with the followed motion.
        double x = -error;
        double v = velocity - carried;
        double decay = Math.exp(-omega * dt);
        double k = v + omega * x;
        double nextX = (x + k * dt) * decay;
        double nextV = (v - omega * k * dt) * decay;
        velocity = nextV + carried;
        return nextX - x + carried * dt;
    }

    /** The spring's current turn speed, in degrees per second. */
    public double velocity() {
        return velocity;
    }

    /** Caps the turn speed so a bad frame can't whip the camera around. */
    public void limitVelocity(double maxDegreesPerSecond) {
        velocity = Math.max(-maxDegreesPerSecond, Math.min(maxDegreesPerSecond, velocity));
    }

    public void reset() {
        velocity = 0;
    }
}
