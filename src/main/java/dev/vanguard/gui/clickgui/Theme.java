package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Glass;
import dev.vanguard.module.modules.client.ClickGuiModule;

/**
 * The glass look: pieces of dark liquid glass floating over the world, white text and controls
 * made of translucent white on top of them, and one accent color for what's switched on.
 *
 * <p>Following how glass interfaces stay readable, only the floating layer (the top bar and the
 * two panes) is real glass. What sits on the glass uses fills, never more glass, except the
 * selection droplets, which are meant to read as a lens on top of the pane.
 */
public final class Theme {
    // Text on glass
    public static final int TEXT = 0xFFF4F6FA;
    public static final int TEXT_DIM = 0xC4E8ECF4;
    public static final int TEXT_MUTED = 0x8CDCE2EC;
    public static final int CAPTION = 0x80DCE2EC;

    // Fills on glass
    public static final int GROUP = 0x10FFFFFF;
    public static final int HAIRLINE = 0x12FFFFFF;
    public static final int HOVER = 0x0DFFFFFF;
    public static final int CONTROL = 0x1CFFFFFF;
    public static final int CONTROL_HOVER = 0x30FFFFFF;
    public static final int FIELD = 0x24000000;
    public static final int SWITCH_OFF = 0x38FFFFFF;
    public static final int TRACK = 0x2AFFFFFF;
    public static final int KNOB = 0xFFFFFFFF;
    public static final int TILE = 0x1FFFFFFF;
    public static final int DANGER = 0xFFFF6961;

    // Popovers (tooltips): not glass, so they read over anything.
    public static final int POPOVER = 0xF0161A22;
    public static final int POPOVER_EDGE = 0x1CFFFFFF;

    public static final int DEFAULT_ACCENT = 0xFF4FA8FF;

    /** The top bar's capsules. */
    public final Glass capsule = new Glass().bezel(15f).thickness(2.2f).frost(0.72f).tint(0x7A0E1118).shadow(14f);
    /** The module list and the settings pane. */
    public final Glass pane = new Glass().bezel(16f).thickness(1.9f).frost(0.9f).tint(0x940D1016).shadow(22f);
    /** The lens that marks the selected tab or module. */
    public final Glass droplet = new Glass().bezel(7f).thickness(2.4f).frost(1f).specular(1f).tint(0x6C4A5262).shadow(5f);

    private int accent = DEFAULT_ACCENT;
    private float refraction = 1f;
    private float frost = 1f;
    private float tint = 1f;

    void update(ClickGuiModule settings) {
        if (settings.rainbow.isOn()) {
            double periodMs = settings.rainbowSpeed.get() * 1000.0;
            float hue = (float) ((System.currentTimeMillis() % (long) periodMs) / periodMs);
            accent = Colors.hsv(hue, 0.6f, 1f, 255);
        } else {
            accent = settings.accent.argb();
        }
        refraction = settings.refraction.floatValue() / 100f;
        frost = settings.frost.floatValue() / 100f;
        tint = settings.tint.floatValue() / 55f;
    }

    public int accent() {
        return accent;
    }

    public int accent(int alpha) {
        return Colors.withAlpha(accent, alpha);
    }

    /** A deeper shade of the accent, for the bottom of accent-filled tiles. */
    public int accentDeep() {
        return Colors.lerp(accent, 0xFF0B1A3A, 0.35f);
    }

    /**
     * {@code base} adjusted by the player's glass settings, with its opacity (the materialize
     * animation) scaling how strongly it bends light.
     */
    public Glass styled(Glass base, Glass out, float materialize) {
        out.bezel = base.bezel;
        out.thickness = base.thickness * refraction * materialize;
        out.frost = Math.clamp(base.frost * frost, 0f, 1f);
        out.specular = base.specular;
        int tintAlpha = Math.clamp(Math.round(Colors.alpha(base.tint) * tint), 0, 255);
        out.tint = Colors.withAlpha(base.tint, tintAlpha);
        out.shadow = base.shadow;
        out.glow = 0;
        out.cursorX = out.cursorY = Float.NaN;
        return out;
    }
}
