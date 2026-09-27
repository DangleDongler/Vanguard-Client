package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.render.Colors;
import dev.vanguard.module.modules.client.ClickGuiModule;

/** ClickGUI palette. Fixed neutrals plus an accent resolved once per frame. */
public final class Theme {
    public static final int PANEL = 0xF20F0F14;
    public static final int HEADER = 0xFF15151C;
    public static final int SETTINGS_BG = 0xFF0B0B0F;
    public static final int OUTLINE = 0x16FFFFFF;
    public static final int SHADOW = 0x99000000;
    public static final int HOVER = 0x0CFFFFFF;
    public static final int PRESSED = 0x14FFFFFF;
    public static final int TEXT = 0xFFEDEDF3;
    public static final int TEXT_DIM = 0xFF9A9AA8;
    public static final int TEXT_MUTED = 0xFF5F5F6D;
    public static final int TRACK = 0xFF272731;
    public static final int FIELD = 0xFF17171F;
    public static final int KNOB = 0xFFF4F4F8;

    private int accent = 0xFF7B61FF;
    private int accentSecondary = 0xFFB061FF;

    void update(ClickGuiModule settings) {
        if (settings.rainbow.isOn()) {
            double periodMs = settings.rainbowSpeed.get() * 1000.0;
            float hue = (float) ((System.currentTimeMillis() % (long) periodMs) / periodMs);
            accent = Colors.hsv(hue, 0.62f, 1f, 255);
            accentSecondary = Colors.hsv(hue + 0.08f, 0.62f, 1f, 255);
        } else {
            accent = settings.accent.argb();
            float[] hsv = Colors.toHsv(accent);
            // A neighbouring hue gives gradients some depth without clashing.
            accentSecondary = Colors.hsv(hsv[0] + 0.07f, hsv[1], Math.min(1f, hsv[2] * 1.05f), 255);
        }
    }

    public int accent() {
        return accent;
    }

    public int accent(int alpha) {
        return Colors.withAlpha(accent, alpha);
    }

    public int accentSecondary() {
        return accentSecondary;
    }
}
