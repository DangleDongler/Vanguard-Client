package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.clickgui.widget.Widgets;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Module;
import dev.vanguard.setting.Setting;
import dev.vanguard.util.Keys;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A module row: name, bound key and an on/off switch. Clicking the row toggles the module;
 * the arrow that appears on hover (or a right-click) opens its settings in an inset card.
 */
final class ModuleButton {
    static final float HEIGHT = 17f;
    private static final float INSET = 5f;
    private static final float SWITCH_W = 15f;
    private static final float SWITCH_H = 8f;
    private static final float ARROW_SIZE = 11f;
    private static final float CARD_PAD = 3f;
    private static final float CARD_GAP = 3f;

    private final Module module;
    private final List<Widget> widgets = new ArrayList<>();
    private final Animation enabled;
    private final Animation hover = new Animation(0, 140, Easing.LINEAR);
    private final Animation arrowHover = new Animation(0, 120, Easing.LINEAR);
    private final Animation expand = new Animation(0, 260, Easing.QUINT_OUT);
    private final Animation searchFade = new Animation(1, 180, Easing.CUBIC_OUT);
    private boolean expanded;

    private float x, y, width;

    ModuleButton(Module module) {
        this.module = module;
        for (Setting<?> setting : module.settings()) widgets.add(Widgets.create(setting));
        widgets.add(Widgets.create(module.bind()));
        this.enabled = new Animation(module.isEnabled() ? 1 : 0, 200, Easing.CUBIC_OUT);
    }

    Module module() {
        return module;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    boolean matches(String query) {
        return query.isEmpty() || module.name().toLowerCase(Locale.ROOT).contains(query);
    }

    /** Fades rows in and out as the search filter changes. */
    void updateSearch(String query) {
        searchFade.animateTo(matches(query) ? 1 : 0);
    }

    private float settingsHeight() {
        float h = CARD_PAD * 2;
        for (Widget widget : widgets) {
            if (widget.isVisible()) h += widget.height();
        }
        return h;
    }

    float height() {
        return (HEIGHT + (settingsHeight() + CARD_GAP) * expand.get()) * searchFade.get();
    }

    private float switchX() {
        return x + width - 11f - SWITCH_W;
    }

    private float arrowX() {
        return switchX() - 4f - ARROW_SIZE;
    }

    void layout(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    void render(GuiContext ctx, float x, float y, float width) {
        layout(x, y, width);
        float visibility = searchFade.get();
        if (visibility <= 0.001f) return;

        Render2D r = ctx.render;
        Theme theme = ctx.theme;
        r.pushAlpha(visibility);

        enabled.animateTo(module.isEnabled() ? 1 : 0);
        expand.animateTo(expanded ? 1 : 0);
        boolean hovered = ctx.hovered(x, y, width, HEIGHT);
        boolean overArrow = hovered && ctx.hovered(arrowX(), y, ARROW_SIZE, HEIGHT);
        hover.animateTo(hovered ? 1 : 0);
        arrowHover.animateTo(overArrow ? 1 : 0);
        if (hovered) {
            ctx.tooltip(overArrow ? "Settings" : module.description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        float on = enabled.get();
        float h = hover.get();
        float e = expand.get();

        r.roundedRect(x + INSET, y + 1f, width - INSET * 2, HEIGHT - 2f, 4f, Colors.fade(Theme.HOVER, Math.max(h, e * 0.6f)));

        int nameColor = Colors.lerp(Colors.lerp(Theme.TEXT_DIM, 0xFFC9C8D3, h), Theme.TEXT, on);
        float nameRight = arrowX() - 4f;
        if (module.bind().isBound()) {
            String key = Keys.name(module.bind().key());
            float keyWidth = r.smallWidth(key);
            float badgeX = arrowX() - keyWidth - 6f;
            r.roundedRect(badgeX - 3f, y + 4.5f, keyWidth + 6f, HEIGHT - 9f, 2.5f, Colors.fade(Theme.FIELD, 0.9f));
            r.small(key, badgeX, r.smallY(y, HEIGHT), Theme.TEXT_MUTED);
            nameRight = badgeX - 6f;
        }
        r.text(r.ellipsize(module.name(), nameRight - (x + 12f), false), x + 12f, r.textY(y, HEIGHT), nameColor);

        // The settings arrow only shows while it's useful: on hover, or while open.
        float arrowAlpha = Math.max(h, e);
        if (arrowAlpha > 0.01f) {
            float ax = arrowX() + ARROW_SIZE / 2f, ay = y + HEIGHT / 2f;
            r.pushAlpha(arrowAlpha);
            r.circle(ax, ay, ARROW_SIZE / 2f, Colors.withAlpha(0xFFFFFF, Math.round(26 * arrowHover.get())));
            r.chevron(ax, ay, 4.5f, (float) (Math.PI / 2 * e), 1.1f, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, Math.max(arrowHover.get(), e)));
            r.popAlpha();
        }

        float sx = switchX(), sy = y + (HEIGHT - SWITCH_H) / 2f;
        r.roundedRect(sx, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2f, Colors.lerp(Theme.TRACK, theme.accent(), on));
        if (on > 0.01f) r.shadow(sx, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2f, 4f, theme.accent(Math.round(60 * on)));
        r.circle(sx + SWITCH_H / 2f + (SWITCH_W - SWITCH_H) * on, sy + SWITCH_H / 2f, SWITCH_H / 2f - 1.5f,
            Colors.lerp(0xFFB9B8C6, Theme.KNOB, on));

        if (e > 0.001f) drawSettings(ctx, e);
        r.popAlpha();
    }

    private void drawSettings(GuiContext ctx, float progress) {
        Render2D r = ctx.render;
        float cardX = x + INSET, cardY = y + HEIGHT, cardW = width - INSET * 2;
        float visible = settingsHeight() * progress;
        r.pushScissor(cardX, cardY, cardW, visible);
        r.roundedRect(cardX, cardY, cardW, settingsHeight(), 5f, Theme.CARD);
        r.roundedOutline(cardX, cardY, cardW, settingsHeight(), 5f, 0.6f, Theme.SEPARATOR);
        r.pushAlpha(Math.min(1f, progress * 1.4f));

        // Widgets below the clipped area still get laid out, but must not claim hover.
        float clipBottom = cardY + visible;
        float realMouseY = ctx.mouseY;
        float wy = cardY + CARD_PAD;
        for (Widget widget : widgets) {
            if (!widget.isVisible()) continue;
            if (ctx.mouseY >= clipBottom) ctx.mouseY = Float.MAX_VALUE;
            widget.render(ctx, cardX, wy, cardW);
            ctx.mouseY = realMouseY;
            wy += widget.height();
        }
        r.popAlpha();
        r.popScissor();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchFade.target() == 0) return false;
        if (mouseX < x || mouseX >= x + width) return false;
        if (mouseY >= y && mouseY < y + HEIGHT) {
            boolean onArrow = mouseX >= arrowX() && mouseX < arrowX() + ARROW_SIZE;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_LEFT && onArrow) {
                expanded = !expanded;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                module.toggle();
            } else {
                return false;
            }
            return true;
        }
        float settingsTop = y + HEIGHT;
        if (!expanded || mouseY < settingsTop || mouseY >= settingsTop + settingsHeight() * expand.get()) return false;
        for (Widget widget : widgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true;
    }

    void mouseReleased(double mouseX, double mouseY, int button) {
        for (Widget widget : widgets) widget.mouseReleased(mouseX, mouseY, button);
    }

    void clickedOutside() {
        for (Widget widget : widgets) widget.clickedOutside();
    }

    boolean keyPressed(int key, int modifiers) {
        for (Widget widget : widgets) {
            if (widget.keyPressed(key, modifiers)) return true;
        }
        return false;
    }

    boolean isCapturingKeyboard() {
        for (Widget widget : widgets) {
            if (widget.isCapturingKeyboard()) return true;
        }
        return false;
    }
}
