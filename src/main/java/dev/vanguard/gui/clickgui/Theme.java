package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.render.Colors;
import dev.vanguard.module.modules.client.ClickGuiModule;

/**
 * ClickGUI palette: dark, slightly see-through neutrals over the blurred world, and an accent
 * (light grey by default) for switches and sliders.
 */
public final class Theme {
    // Sidebar
    public static final int SIDEBAR_FRAME = 0xEE171716;
    public static final int SIDEBAR_CAP_TOP = 0xF4211F1C;
    public static final int SIDEBAR = 0xEC0E0F10;
    public static final int OUTLINE = 0x12FFFFFF;
    public static final int SEPARATOR = 0x10FFFFFF;
    public static final int SELECTED = 0xFF1E1E1E;

    // Page
    public static final int HEADER = 0xDD1A1917;
    public static final int CHIP = 0xFF2A2A27;
    public static final int CARD = 0xE60B0B0B;
    public static final int CARD_HEADER = 0xFF1C1C1C;
    public static final int SHADOW = 0x80000000;

    // Controls
    public static final int FIELD = 0xFF171717;
    public static final int BUTTON = 0xFF262626;
    public static final int BUTTON_HOVER = 0xFF323232;
    public static final int SWITCH_OFF = 0xFF2E302F;
    public static final int KNOB_OFF = 0xFF9A9C9B;
    public static final int TRACK = 0xFF2F2F2F;
    public static final int HOVER = 0x09FFFFFF;
    public static final int DANGER = 0xFFE5534B;

    // Text
    public static final int TEXT = 0xFFE4E4E4;
    public static final int TEXT_DIM = 0xFFA6A6A6;
    public static final int TEXT_MUTED = 0xFF6C6C6C;
    public static final int CAPTION = 0xFF6E6E6B;
    public static final int HEADER_TEXT = 0xFF8E8E8A;

    private int accent = 0xFFCACACA;

    void update(ClickGuiModule settings) {
        if (settings.rainbow.isOn()) {
            double periodMs = settings.rainbowSpeed.get() * 1000.0;
            float hue = (float) ((System.currentTimeMillis() % (long) periodMs) / periodMs);
            accent = Colors.hsv(hue, 0.55f, 0.95f, 255);
        } else {
            accent = settings.accent.argb();
        }
    }

    public int accent() {
        return accent;
    }

    public int accent(int alpha) {
        return Colors.withAlpha(accent, alpha);
    }

    /** A dark version of the accent, for the track of a switch that's on. */
    public int accentTrack() {
        return Colors.lerp(SWITCH_OFF, accent, 0.38f);
    }
}
