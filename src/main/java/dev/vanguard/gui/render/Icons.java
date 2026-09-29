package dev.vanguard.gui.render;

import dev.vanguard.module.Category;

/**
 * Line icons drawn from shapes, so they stay sharp at any scale. Each fits a
 * {@code size} x {@code size} box centered on (cx, cy).
 */
public final class Icons {
    private static final float STROKE = 0.11f;

    private Icons() {
    }

    public static void category(Render2D r, Category category, float cx, float cy, float size, int color) {
        switch (category) {
            case COMBAT -> crosshair(r, cx, cy, size, color);
            case MOVEMENT -> speed(r, cx, cy, size, color);
            case RENDER -> eye(r, cx, cy, size, color);
            case PLAYER -> person(r, cx, cy, size, color);
            case WORLD -> globe(r, cx, cy, size, color);
            case MISC -> grid(r, cx, cy, size, color);
            case CLIENT -> sliders(r, cx, cy, size, color);
        }
    }

    /** Ring with ticks crossing it, like a scope. */
    public static void crosshair(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        r.ring(cx, cy, s * 0.32f, t, c);
        r.line(cx, cy - s * 0.5f, cx, cy - s * 0.17f, t, c);
        r.line(cx, cy + s * 0.17f, cx, cy + s * 0.5f, t, c);
        r.line(cx - s * 0.5f, cy, cx - s * 0.17f, cy, t, c);
        r.line(cx + s * 0.17f, cy, cx + s * 0.5f, cy, t, c);
    }

    /** Double chevron. */
    public static void speed(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        r.chevron(cx - s * 0.16f, cy, s * 0.72f, 0, t, c);
        r.chevron(cx + s * 0.2f, cy, s * 0.72f, 0, t, c);
    }

    /** Lens outline with a pupil. */
    public static void eye(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float w = s * 0.48f, k = s * 0.38f;
        float radius = (float) Math.sqrt(w * w + k * k);
        float spread = (float) Math.atan2(w, k);
        r.arc(cx, cy + k, radius, (float) (-Math.PI / 2 - spread), (float) (-Math.PI / 2 + spread), t, c);
        r.arc(cx, cy - k, radius, (float) (Math.PI / 2 - spread), (float) (Math.PI / 2 + spread), t, c);
        r.circle(cx, cy, s * 0.14f, c);
    }

    /** Head and shoulders. */
    public static void person(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        r.ring(cx, cy - s * 0.2f, s * 0.19f, t, c);
        r.arc(cx, cy + s * 0.45f, s * 0.32f, (float) Math.PI, (float) (Math.PI * 2), t, c);
    }

    /** Globe: outline, equator and one meridian. */
    public static void globe(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float radius = s * 0.42f;
        r.ring(cx, cy, radius, t, c);
        r.line(cx - radius, cy, cx + radius, cy, t, c);
        int steps = 12;
        float[] meridian = new float[(steps + 1) * 2];
        for (int i = 0; i <= steps; i++) {
            double a = -Math.PI / 2 + Math.PI * i / steps;
            meridian[i * 2] = cx + (float) Math.cos(a) * radius * 0.42f;
            meridian[i * 2 + 1] = cy + (float) Math.sin(a) * radius;
        }
        r.polyline(t, c, meridian);
        for (int i = 0; i <= steps; i++) meridian[i * 2] = 2 * cx - meridian[i * 2];
        r.polyline(t, c, meridian);
    }

    /** Four tiles. */
    public static void grid(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float tile = s * 0.36f, gap = s * 0.12f;
        float x0 = cx - tile - gap / 2f, y0 = cy - tile - gap / 2f;
        for (int i = 0; i < 4; i++) {
            float x = x0 + (i % 2) * (tile + gap), y = y0 + (i / 2) * (tile + gap);
            r.roundedOutline(x, y, tile, tile, tile * 0.3f, t, c);
        }
    }

    /** Three slider tracks with knobs at different positions. */
    public static void sliders(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float half = s * 0.42f;
        float[] knobs = {0.3f, -0.25f, 0.1f};
        for (int i = 0; i < 3; i++) {
            float y = cy + (i - 1) * s * 0.32f;
            r.line(cx - half, y, cx + half, y, t, c);
            r.circle(cx + knobs[i] * s, y, s * 0.12f, c);
        }
    }

    public static void search(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float radius = s * 0.3f;
        float ox = cx - s * 0.08f, oy = cy - s * 0.08f;
        r.ring(ox, oy, radius, t, c);
        float d = radius * 0.72f;
        r.line(ox + d + t * 0.3f, oy + d + t * 0.3f, cx + s * 0.42f, cy + s * 0.42f, t * 1.15f, c);
    }

    /** The Vanguard mark: a bold V with rounded strokes. */
    public static void logo(Render2D r, float cx, float cy, float s, int c) {
        float t = s * 0.21f;
        float top = cy - s * 0.34f, bottom = cy + s * 0.36f, half = s * 0.40f;
        r.line(cx - half, top, cx, bottom, t, c);
        r.line(cx, bottom, cx + half, top, t, c);
    }

    /** A sheet with a folded corner and two lines of text. */
    public static void file(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float w = s * 0.62f, h = s * 0.8f, x = cx - w / 2f, y = cy - h / 2f;
        r.roundedOutline(x, y, w, h, s * 0.1f, t, c);
        r.line(x + w * 0.28f, cy - s * 0.05f, x + w * 0.72f, cy - s * 0.05f, t, c);
        r.line(x + w * 0.28f, cy + s * 0.15f, x + w * 0.6f, cy + s * 0.15f, t, c);
    }

    public static void folder(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float w = s * 0.84f, h = s * 0.6f, x = cx - w / 2f, y = cy - h / 2f + s * 0.06f;
        r.roundedRect(x, y - s * 0.12f, w * 0.42f, s * 0.2f, s * 0.06f, c);
        r.roundedOutline(x, y, w, h, s * 0.1f, t, c);
    }

    public static void close(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float h = s * 0.32f;
        r.line(cx - h, cy - h, cx + h, cy + h, t, c);
        r.line(cx - h, cy + h, cx + h, cy - h, t, c);
    }
}
