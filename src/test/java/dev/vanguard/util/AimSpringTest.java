package dev.vanguard.util;

import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimSpringTest {
    private static final double OMEGA = 12;

    /** Runs the spring toward {@code goal(t)} and returns the final angle. */
    private static double simulate(AimSpring spring, DoubleUnaryOperator goal, double follow, double fps, double seconds) {
        double dt = 1.0 / fps;
        int frames = (int) Math.round(seconds * fps);
        double current = 0;
        for (int i = 0; i < frames; i++) {
            double t = i * dt;
            double rate = (goal.applyAsDouble(t + dt) - goal.applyAsDouble(t)) / dt;
            current += spring.step(goal.applyAsDouble(t) - current, rate, follow, OMEGA, dt);
        }
        return current;
    }

    @Test
    void settlesOnTargetWithoutOvershoot() {
        AimSpring spring = new AimSpring();
        double dt = 1 / 240.0;
        double current = 0;
        double previous = 0;
        for (int i = 0; i < 360; i++) {
            current += spring.step(30 - current, 0, 0, OMEGA, dt);
            assertTrue(current >= previous - 1e-9, "should only ever move toward the target");
            assertTrue(current <= 30 + 1e-9, "should never overshoot");
            previous = current;
        }
        assertEquals(30, current, 0.01);
    }

    @Test
    void sameMotionAtAnyFrameRate() {
        double slow = simulate(new AimSpring(), t -> 30, 0, 30, 0.5);
        double fast = simulate(new AimSpring(), t -> 30, 0, 240, 0.5);
        assertEquals(slow, fast, 1e-6);
    }

    @Test
    void startsGentlyInsteadOfJumping() {
        AimSpring spring = new AimSpring();
        double first = spring.step(30, 0, 0, OMEGA, 1 / 240.0);
        // Turn speed ramps up from zero, so the first frame barely moves.
        assertTrue(first > 0 && first < 0.1, "first frame moved " + first);
        double second = spring.step(30 - first, 0, 0, OMEGA, 1 / 240.0);
        assertTrue(second > first, "should accelerate smoothly");
    }

    @Test
    void tracksAMovingTargetWithoutLag() {
        AimSpring spring = new AimSpring();
        // After the last frame the goal has reached 60 degrees (one second at 60 degrees per second).
        double end = simulate(spring, t -> 60 * t, 1, 144, 1.0);
        assertEquals(60, end, 0.05);
        assertEquals(60, spring.velocity(), 0.5);
    }

    @Test
    void coastsToAStopWhenTheTargetIsLost() {
        AimSpring spring = new AimSpring();
        simulate(spring, t -> 30, 0, 240, 0.1);
        double moving = spring.velocity();
        assertTrue(moving > 20, "should be turning by now");

        double drift = 0;
        for (int i = 0; i < 120; i++) drift += spring.step(0, 0, 0, OMEGA, 1 / 240.0);
        assertTrue(Math.abs(spring.velocity()) < 1, "should come to rest");
        assertTrue(drift > 0 && drift < moving / OMEGA * 1.5, "should glide to a stop, drifted " + drift);
    }

    @Test
    void ignoresEmptyFrames() {
        AimSpring spring = new AimSpring();
        assertEquals(0, spring.step(30, 0, 0, OMEGA, 0));
        assertEquals(0, spring.velocity());
    }
}
