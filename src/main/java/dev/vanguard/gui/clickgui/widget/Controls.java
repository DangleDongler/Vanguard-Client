package dev.vanguard.gui.clickgui.widget;

import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;

/** Controls drawn in more than one place. */
public final class Controls {
    private Controls() {
    }

    /**
     * A capsule switch. {@code on} runs from 0 (off) to 1 (on) as it animates; the knob stretches
     * mid-way, like a drop of liquid sliding across.
     */
    public static void drawSwitch(GuiContext ctx, float x, float y, float w, float h, float on, float hover) {
        Render2D r = ctx.render;
        int accent = ctx.theme.accent();
        int off = Colors.lerp(Theme.SWITCH_OFF, 0x4DFFFFFF, hover * 0.5f);
        r.roundedRect(x, y, w, h, h / 2f, Colors.lerp(off, accent, on));
        // A little light on the upper half of a switch that's on.
        if (on > 0.01f) r.roundedGradientV(x, y, w, h, h / 2f, Colors.withAlpha(0xFFFFFF, Math.round(46 * on)), 0x00FFFFFF);

        float inset = 1.6f;
        float knob = h - inset * 2f;
        float stretch = knob * 0.45f * (float) Math.sin(Math.PI * on);
        float travel = w - inset * 2f - knob;
        float kx = x + inset + travel * on - stretch * on;
        float ky = y + inset;
        r.shadow(kx, ky + 0.4f, knob + stretch, knob, knob / 2f, 2.2f, 0x50000000);
        r.roundedRect(kx, ky, knob + stretch, knob, knob / 2f, Theme.KNOB);
    }
}
