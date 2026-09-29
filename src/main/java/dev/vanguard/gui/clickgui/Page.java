package dev.vanguard.gui.clickgui;

import java.util.List;

/** What the content area shows: a module's settings, or the configs. */
interface Page {
    /** Shown in the chip at the start of the header bar. */
    String title();

    /** Shown after the chip. */
    String description();

    List<Section> sections();

    /** Called each time the page is opened. */
    default void onShow() {
    }
}
