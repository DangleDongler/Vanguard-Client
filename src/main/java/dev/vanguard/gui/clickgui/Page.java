package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Module;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** What the settings pane shows: a module's settings, or the configs. */
interface Page {
    String title();

    /** Shown under the title. */
    String description();

    List<Section> sections();

    /** Draws the page's icon centered at (cx, cy) in a {@code size} box. */
    void icon(Render2D render, float cx, float cy, float size, int color);

    /** The module this page belongs to, if any. */
    default @Nullable Module module() {
        return null;
    }

    /** Called each time the page is opened. */
    default void onShow() {
    }
}
