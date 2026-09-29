package dev.vanguard.gui.clickgui.widget;

import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;

/** A line of muted text, such as "No saved configs yet." */
public final class NoteWidget extends Widget {
    public NoteWidget(String text) {
        super(null, text, "");
    }

    @Override
    public float height() {
        return ROW_HEIGHT;
    }

    @Override
    protected void draw(GuiContext ctx) {
        drawLabel(ctx, y, ROW_HEIGHT, 0, Theme.TEXT_MUTED);
    }
}
