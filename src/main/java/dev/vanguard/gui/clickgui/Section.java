package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A group of rows on a page: a small caption, then the rows on a faint rounded fill with hairlines
 * between them. Rows that come and go (settings shown only while another is on) slide open and
 * closed.
 */
final class Section {
    static final float CAPTION_HEIGHT = 17f;
    private static final float RADIUS = 11f;
    private static final float PAD_Y = 3f;
    /** Rows fade out over this distance as they scroll under the pane's edges. */
    private static final float EDGE_FADE = 14f;

    private final String title;
    private List<Widget> widgets = new ArrayList<>();
    private final Map<Widget, Animation> presence = new IdentityHashMap<>();

    Section(String title, List<Widget> widgets) {
        this.title = title.toUpperCase(Locale.ROOT);
        setWidgets(widgets);
    }

    void setWidgets(List<Widget> widgets) {
        this.widgets = new ArrayList<>(widgets);
        presence.keySet().retainAll(this.widgets);
        for (Widget widget : this.widgets) {
            presence.computeIfAbsent(widget, w -> new Animation(w.isVisible() ? 1 : 0, 220, Easing.CUBIC_OUT));
        }
    }

    List<Widget> widgets() {
        return widgets;
    }

    private float presence(Widget widget) {
        Animation animation = presence.get(widget);
        animation.animateTo(widget.isVisible() ? 1 : 0);
        return animation.get();
    }

    private float rowsHeight() {
        float height = 0;
        for (Widget widget : widgets) height += widget.height() * presence(widget);
        return height;
    }

    /** The section's height this frame. */
    float height() {
        return CAPTION_HEIGHT + PAD_Y * 2 + rowsHeight();
    }

    /** Draws the section with its top at {@code y}; rows near {@code viewTop} or {@code viewBottom} fade out. */
    void render(GuiContext ctx, float x, float y, float width, float viewTop, float viewBottom) {
        Render2D r = ctx.render;
        r.pushAlpha(edgeFade(y + CAPTION_HEIGHT / 2f, viewTop, viewBottom));
        r.tiny(title, x + 11f, y + 5f, Theme.CAPTION);
        r.popAlpha();

        float groupY = y + CAPTION_HEIGHT;
        r.roundedRect(x, groupY, width, rowsHeight() + PAD_Y * 2, RADIUS, Theme.GROUP);

        float cy = groupY + PAD_Y;
        boolean first = true;
        for (Widget widget : widgets) {
            float shown = presence(widget);
            if (shown <= 0.001f) continue;
            float rowHeight = widget.height() * shown;
            float fade = edgeFade(cy + Math.min(rowHeight, Widget.ROW_HEIGHT) / 2f, viewTop, viewBottom);
            if (!first) r.rect(x + Widget.PAD_X, cy, width - Widget.PAD_X * 2, 0.5f, Colors.fade(Theme.HAIRLINE, shown * fade));
            first = false;
            if (fade <= 0.001f) {
                cy += rowHeight;
                continue;
            }
            boolean clip = shown < 0.999f;
            if (clip) r.pushScissor(x, cy, width, rowHeight);
            r.pushAlpha(shown * fade);
            widget.render(ctx, x, cy, width);
            r.popAlpha();
            if (clip) r.popScissor();
            cy += rowHeight;
        }
    }

    private static float edgeFade(float rowCenter, float viewTop, float viewBottom) {
        float top = Math.clamp((rowCenter - viewTop) / EDGE_FADE, 0f, 1f);
        float bottom = Math.clamp((viewBottom - rowCenter) / EDGE_FADE, 0f, 1f);
        return Math.min(top, bottom);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Widget widget : widgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }
}
