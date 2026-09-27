package dev.vanguard.gui.render;

/** ARGB color helpers. */
public final class Colors {
    private Colors() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }

    public static int alpha(int c) {
        return c >>> 24;
    }

    public static int red(int c) {
        return c >> 16 & 0xFF;
    }

    public static int green(int c) {
        return c >> 8 & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int withAlpha(int c, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (c & 0xFFFFFF);
    }

    /** Multiplies the alpha channel by {@code factor} (0..1). */
    public static int fade(int c, float factor) {
        if (factor >= 1f) return c;
        return withAlpha(c, Math.round(alpha(c) * Math.max(0f, factor)));
    }

    public static int lerp(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return argb(
            Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
            Math.round(red(from) + (red(to) - red(from)) * t),
            Math.round(green(from) + (green(to) - green(from)) * t),
            Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    /** Hue, saturation and value in 0..1, alpha 0..255. */
    public static int hsv(float h, float s, float v, int alpha) {
        h = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) h;
        float f = h - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return argb(alpha, Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    /** Returns {hue, saturation, value} in 0..1. */
    public static float[] toHsv(int c) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0f) h = 0f;
        else if (max == r) h = ((g - b) / delta) / 6f;
        else if (max == g) h = ((b - r) / delta + 2f) / 6f;
        else h = ((r - g) / delta + 4f) / 6f;
        if (h < 0f) h += 1f;
        return new float[] {h, max == 0f ? 0f : delta / max, max};
    }

    public static String hex(int c, boolean withAlpha) {
        return withAlpha ? String.format("#%08X", c) : String.format("#%06X", c & 0xFFFFFF);
    }
}
