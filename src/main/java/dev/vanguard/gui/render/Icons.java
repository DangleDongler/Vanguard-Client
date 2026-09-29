package dev.vanguard.gui.render;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;

import java.util.Arrays;

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
            case COMBAT -> swords(r, cx, cy, size, color);
            case MOVEMENT -> speed(r, cx, cy, size, color);
            case RENDER -> eye(r, cx, cy, size, color);
            case PLAYER -> person(r, cx, cy, size, color);
            case WORLD -> globe(r, cx, cy, size, color);
            case MISC -> grid(r, cx, cy, size, color);
            case CLIENT -> sliders(r, cx, cy, size, color);
        }
    }

    /** A module's own icon, or its category's when it has none. */
    public static void module(Render2D r, Module module, float cx, float cy, float size, int color) {
        switch (module.name()) {
            case "AimAssist" -> crosshair(r, cx, cy, size, color);
            case "TriggerBot" -> bolt(r, cx, cy, size, color);
            case "ShieldBreaker" -> shield(r, cx, cy, size, color);
            case "SprintReset" -> sprint(r, cx, cy, size, color);
            case "ClickGUI" -> sliders(r, cx, cy, size, color);
            default -> category(r, module.category(), cx, cy, size, color);
        }
    }

    /** Two crossed swords. */
    public static void swords(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float a = s * 0.42f;
        r.line(cx - a, cy - a, cx + a * 0.62f, cy + a * 0.62f, t, c);
        r.line(cx + a, cy - a, cx - a * 0.62f, cy + a * 0.62f, t, c);
        // Guards across each blade near the hilt, then the hilts.
        float g = s * 0.16f, h = a * 0.52f;
        r.line(cx + h - g, cy + h + g, cx + h + g, cy + h - g, t, c);
        r.line(cx - h - g, cy + h - g, cx - h + g, cy + h + g, t, c);
        r.line(cx + h, cy + h, cx + a * 0.95f, cy + a * 0.95f, t, c);
        r.line(cx - h, cy + h, cx - a * 0.95f, cy + a * 0.95f, t, c);
    }

    /** A lightning bolt. */
    public static void bolt(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        r.polyline(t, c,
            cx + s * 0.10f, cy - s * 0.48f,
            cx - s * 0.22f, cy + s * 0.05f,
            cx + s * 0.20f, cy + s * 0.02f,
            cx - s * 0.10f, cy + s * 0.48f);
    }

    /** A shield with a crack down it. */
    public static void shield(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float w = s * 0.36f, top = cy - s * 0.42f, shoulder = cy - s * 0.02f, bottom = cy + s * 0.46f;
        int steps = 8;
        float[] outline = new float[(steps * 2 + 5) * 2];
        int i = 0;
        outline[i++] = cx - w;
        outline[i++] = top + s * 0.05f;
        outline[i++] = cx;
        outline[i++] = top - s * 0.02f;
        outline[i++] = cx + w;
        outline[i++] = top + s * 0.05f;
        // Right side curving into the point, then back up the left.
        for (int k = 0; k <= steps; k++) {
            double a = Math.PI / 2 * k / steps;
            outline[i++] = cx + w * (float) Math.cos(a);
            outline[i++] = shoulder + (bottom - shoulder) * (float) Math.sin(a);
        }
        for (int k = steps - 1; k >= 0; k--) {
            double a = Math.PI / 2 * k / steps;
            outline[i++] = cx - w * (float) Math.cos(a);
            outline[i++] = shoulder + (bottom - shoulder) * (float) Math.sin(a);
        }
        outline[i++] = cx - w;
        outline[i++] = top + s * 0.05f;
        r.polyline(t, c, Arrays.copyOf(outline, i));
        r.polyline(t * 0.9f, c, cx + s * 0.02f, top + s * 0.08f, cx - s * 0.08f, cy - s * 0.06f, cx + s * 0.07f, cy + s * 0.08f, cx - s * 0.02f, cy + s * 0.3f);
    }

    /** Two arrows chasing each other round a circle: a reset. */
    public static void sprint(Render2D r, float cx, float cy, float s, int c) {
        float t = s * STROKE;
        float radius = s * 0.34f;
        for (int k = 0; k < 2; k++) {
            float start = (float) (k * Math.PI - Math.PI * 0.42);
            float end = start + (float) (Math.PI * 0.72);
            r.arc(cx, cy, radius, start, end, t, c);
            float ex = cx + (float) Math.cos(end) * radius, ey = cy + (float) Math.sin(end) * radius;
            // Arrowhead pointing along the arc.
            float dx = -(float) Math.sin(end), dy = (float) Math.cos(end);
            float head = s * 0.17f;
            r.polyline(t, c,
                ex - dx * head + dy * head * 0.8f, ey - dy * head - dx * head * 0.8f,
                ex, ey,
                ex - dx * head - dy * head * 0.8f, ey - dy * head + dx * head * 0.8f);
        }
    }

    /** A check mark. */
    public static void check(Render2D r, float cx, float cy, float s, int c) {
        float t = s * 0.13f;
        r.polyline(t, c, cx - s * 0.34f, cy + s * 0.02f, cx - s * 0.1f, cy + s * 0.26f, cx + s * 0.36f, cy - s * 0.24f);
    }

    /** Small chevrons up and down, marking a value that opens a list. */
    public static void upDown(Render2D r, float cx, float cy, float s, int c) {
        float t = s * 0.12f;
        r.chevron(cx, cy - s * 0.2f, s * 0.5f, (float) -Math.PI / 2, t, c);
        r.chevron(cx, cy + s * 0.2f, s * 0.5f, (float) Math.PI / 2, t, c);
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
