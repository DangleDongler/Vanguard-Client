package dev.vanguard.gui.clickgui.widget;

import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.setting.Setting;

/**
 * A setting row inside an expanded module. Widgets lay themselves out while rendering
 * and hit-test input against the bounds from the last frame.
 */
public abstract class Widget {
    public static final float PAD_X = 8f;
    public static final float ROW_HEIGHT = 14f;

    protected final Setting<?> setting;
    protected float x;
    protected float y;
    protected float width;

    protected Widget(Setting<?> setting) {
        this.setting = setting;
    }

    public Setting<?> setting() {
        return setting;
    }

    public boolean isVisible() {
        return setting.isVisible();
    }

    /** Current (possibly animating) height. */
    public abstract float height();

    public final void render(GuiContext ctx, float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
        draw(ctx);
    }

    protected abstract void draw(GuiContext ctx);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    /** Called before every click is dispatched, so widgets can drop focus. */
    public void clickedOutside() {
    }

    /** Returns true when the key was consumed, e.g. while a keybind widget is listening. */
    public boolean keyPressed(int key, int modifiers) {
        return false;
    }

    public boolean charTyped(int codepoint) {
        return false;
    }

    /** Widgets that are waiting for input (keybind listening, text editing) block global shortcuts. */
    public boolean isCapturingKeyboard() {
        return false;
    }

    /**
     * Draws the setting name at the left of a row, shortened with an ellipsis so it never
     * runs into the {@code rightReserve} units of controls on the right.
     */
    protected void drawLabel(GuiContext ctx, float textY, float rightReserve, int color) {
        float available = width - PAD_X * 2 - rightReserve - 4f;
        ctx.render.text(ctx.render.ellipsize(setting.name(), available, false), x + PAD_X, textY, color);
    }

    protected boolean contains(double mouseX, double mouseY, float rx, float ry, float rw, float rh) {
        return mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + rh;
    }

    protected boolean inRow(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
    }
}
