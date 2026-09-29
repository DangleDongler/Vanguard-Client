package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.anim.Spring;

/**
 * The liquid selection marker. Its front edge is on a quick, slightly bouncy spring and its back
 * edge on a slower one, so when the selection moves the drop stretches towards it and snaps back
 * together on arrival, thinning while stretched as liquid would.
 */
final class Droplet {
    private final Spring lead = new Spring(0, 560, 34);
    private final Spring trail = new Spring(0, 200, 27);
    private final Spring extent = new Spring(0, 480, 40);
    private final Animation visible = new Animation(0, 220, Easing.CUBIC_OUT);
    private boolean placed;

    // Result of the last update, along the axis the drop moves on.
    float start;
    float end;
    /** Thickness across the axis, 1 at rest, less while stretched. */
    float thickness = 1f;

    /** Moves the drop to be centered on {@code center}, {@code size} long. */
    void moveTo(float center, float size) {
        if (!placed) {
            lead.snap(center);
            trail.snap(center);
            extent.snap(size);
            placed = true;
            return;
        }
        lead.setTarget(center);
        trail.setTarget(center);
        extent.setTarget(size);
    }

    void show(boolean shown) {
        visible.animateTo(shown ? 1 : 0);
        if (!shown && visible.get() <= 0.001f) placed = false;
    }

    float visibility() {
        return visible.get();
    }

    void update() {
        float a = lead.update(), b = trail.update(), size = extent.update();
        start = Math.min(a, b) - size / 2f;
        end = Math.max(a, b) + size / 2f;
        float stretch = Math.abs(a - b);
        thickness = 1f - 0.24f * Math.min(1f, stretch / Math.max(size, 1f));
    }
}
