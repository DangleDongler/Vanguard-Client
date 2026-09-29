package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.render.Render2D;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A card on a page: a titled strip on top, then rows. Rows that come and go (settings shown only
 * while another is on) slide open and closed.
 */
final class Section {
    static final float HEADER_HEIGHT = 18f;
    private static final float RADIUS = 6f;
    private static final float PAD_TOP = 2f;
    private static final float PAD_BOTTOM = 5f;

    private final String title;
    private List<Widget> widgets = new ArrayList<>();
    private final Map<Widget, Animation> presence = new IdentityHashMap<>();

    Section(String title, List<Widget> widgets) {
        this.title = title;
        setWidgets(widgets);
    }

    void setWidgets(List<Widget> widgets) {
        this.widgets = new ArrayList<>(widgets);
        presence.keySet().retainAll(this.widgets);
        for (Widget widget : this.widgets) {
            presence.computeIfAbsent(widget, w -> new Animation(w.isVisible() ? 1 : 0, 200, Easing.CUBIC_OUT));
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

    /** The card's height this frame. */
    float height() {
        float height = HEADER_HEIGHT + PAD_TOP + PAD_BOTTOM;
        for (Widget widget : widgets) height += widget.height() * presence(widget);
        return height;
    }

    void render(GuiContext ctx, float x, float y, float width) {
        Render2D r = ctx.render;
        float height = height();
        r.shadow(x, y, width, height, RADIUS, 10f, Theme.SHADOW);
        r.roundedRect(x, y, width, height, RADIUS, Theme.CARD);
        r.roundedRect(x, y, width, HEADER_HEIGHT, RADIUS, Theme.CARD_HEADER);
        r.small(title, x + 7f, r.smallY(y, HEADER_HEIGHT), Theme.TEXT_DIM);

        float cy = y + HEADER_HEIGHT + PAD_TOP;
        for (Widget widget : widgets) {
            float shown = presence(widget);
            if (shown <= 0.001f) continue;
            float rowHeight = widget.height() * shown;
            if (shown < 0.999f) {
                r.pushScissor(x, cy, width, rowHeight);
                r.pushAlpha(shown);
            }
            widget.render(ctx, x, cy, width);
            if (shown < 0.999f) {
                r.popAlpha();
                r.popScissor();
            }
            cy += rowHeight;
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Widget widget : widgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }
}
