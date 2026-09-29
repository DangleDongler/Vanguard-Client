package dev.vanguard.gui.clickgui.widget;

import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.setting.Setting;

/**
 * One row in a section of a page: a setting, or a control that isn't a setting (the module's
 * on/off switch, a config entry). Widgets lay themselves out while rendering and hit-test input
 * against the bounds from the last frame.
 */
public abstract class Widget {
    public static final float PAD_X = 11f;
    public static final float ROW_HEIGHT = 22f;

    /** Null for rows that aren't a setting. */
    protected final Setting<?> setting;
    private final String label;
    private final String description;
    protected float x;
    protected float y;
    protected float width;

    protected Widget(Setting<?> setting) {
        this(setting, setting.name(), setting.description());
    }

    protected Widget(Setting<?> setting, String label, String description) {
        this.setting = setting;
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean isVisible() {
        return setting == null || setting.isVisible();
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

    /** A faint highlight behind a hovered row. */
    protected void drawHover(GuiContext ctx, float rowHeight, float hover) {
        if (hover > 0.001f) ctx.render.roundedRect(x + 3f, y + 1f, width - 6f, rowHeight - 2f, 7f, Colors.fade(Theme.HOVER, hover));
    }

    /**
     * Draws the label at the left of a row, shortened with an ellipsis so it never runs into the
     * {@code rightReserve} units of controls on the right.
     */
    protected void drawLabel(GuiContext ctx, float rowY, float rowHeight, float rightReserve, int color) {
        float available = width - PAD_X * 2 - rightReserve - 6f;
        ctx.render.smallBold(ctx.render.ellipsizeSmall(label, available, true), x + PAD_X, ctx.render.smallY(rowY, rowHeight), color);
    }

    protected boolean contains(double mouseX, double mouseY, float rx, float ry, float rw, float rh) {
        return mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + rh;
    }

    protected boolean inRow(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, x, y, width, ROW_HEIGHT);
    }
}
