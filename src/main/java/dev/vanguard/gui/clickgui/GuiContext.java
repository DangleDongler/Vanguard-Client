package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorType;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Glass;
import dev.vanguard.gui.render.Render2D;

/** Per-frame state shared by every ClickGUI element while rendering. */
public final class GuiContext {
    /** How far outside a piece of glass the cursor still lights it, in GUI units. */
    private static final float GLOW_REACH = 24f;

    public final Render2D render;
    public final Theme theme;

    /** Mouse position in GUI units for hover checks. Moved off-screen where another element owns the hover. */
    public float mouseX;
    public float mouseY;

    /** Unmasked mouse position, for drags that continue outside the element that started them. */
    public float rawMouseX;
    public float rawMouseY;

    private final Glass scratch = new Glass();
    private final Animation press = new Animation(0, 520, Easing.CUBIC_OUT);
    private float pressX = Float.NaN;
    private float pressY = Float.NaN;

    private String tooltip;
    private CursorType cursor;

    GuiContext(Render2D render, Theme theme) {
        this.render = render;
        this.theme = theme;
    }

    void beginFrame(float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.rawMouseX = mouseX;
        this.rawMouseY = mouseY;
        this.tooltip = null;
        this.cursor = null;
    }

    /** A click lights up the glass under it, fading out. */
    void pressed(float x, float y) {
        pressX = x;
        pressY = y;
        press.snap(1f);
        press.animateTo(0f);
    }

    public boolean hovered(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Draws a piece of glass in the {@code base} style. {@code materialize} runs 0 to 1 as it
     * appears. The cursor lights it from inside while it's over or near it, and brighter just after
     * a click.
     */
    public void glass(Glass base, float x, float y, float w, float h, float radius, float materialize) {
        Glass glass = theme.styled(base, scratch, materialize);
        float dx = Math.max(Math.max(x - rawMouseX, rawMouseX - (x + w)), 0f);
        float dy = Math.max(Math.max(y - rawMouseY, rawMouseY - (y + h)), 0f);
        float near = Math.clamp(1f - (float) Math.sqrt(dx * dx + dy * dy) / GLOW_REACH, 0f, 1f);
        float glow = 0.5f * near * near;
        float cursorX = rawMouseX, cursorY = rawMouseY;
        float pressGlow = press.get();
        if (pressGlow > 0.001f && pressX >= x && pressX < x + w && pressY >= y && pressY < y + h) {
            glow = Math.min(1f, glow + pressGlow);
            cursorX = pressX;
            cursorY = pressY;
        }
        glass.glow(glow, cursorX, cursorY);
        render.glass(x, y, w, h, radius, glass);
    }

    public void tooltip(String text) {
        if (text != null && !text.isEmpty()) tooltip = text;
    }

    String tooltip() {
        return tooltip;
    }

    public void cursor(CursorType type) {
        cursor = type;
    }

    CursorType cursor() {
        return cursor;
    }
}
