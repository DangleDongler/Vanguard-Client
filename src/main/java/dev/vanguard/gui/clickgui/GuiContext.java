package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorType;
import dev.vanguard.gui.render.Render2D;

/** Per-frame state shared by every ClickGUI element while rendering. */
public final class GuiContext {
    public final Render2D render;
    public final Theme theme;

    /** Mouse position in GUI units for hover checks. Moved off-screen where another element owns the hover. */
    public float mouseX;
    public float mouseY;

    /** Unmasked mouse position, for drags that continue outside the element that started them. */
    public float rawMouseX;
    public float rawMouseY;

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

    public boolean hovered(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
