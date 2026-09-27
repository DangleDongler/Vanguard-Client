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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A module row: left-click toggles, right-click expands its settings. */
final class ModuleButton {
    static final float HEIGHT = 15f;
    private static final float SETTINGS_PAD = 3f;

    private final Module module;
    private final List<Widget> widgets = new ArrayList<>();
    private final Animation enabled;
    private final Animation hover = new Animation(0, 140, Easing.LINEAR);
    private final Animation expand = new Animation(0, 260, Easing.QUINT_OUT);
    private final Animation searchFade = new Animation(1, 180, Easing.CUBIC_OUT);
    private boolean expanded;

    private float x, y, width;

    ModuleButton(Module module) {
        this.module = module;
        for (Setting<?> setting : module.settings()) widgets.add(Widgets.create(setting));
        widgets.add(Widgets.create(module.bind()));
        this.enabled = new Animation(module.isEnabled() ? 1 : 0, 220, Easing.CUBIC_OUT);
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

    /** Fades rows in and out as the search filter changes; returns the current visibility (0..1). */
    float updateSearch(String query) {
        searchFade.animateTo(matches(query) ? 1 : 0);
        return searchFade.get();
    }

    private float settingsHeight() {
        float h = SETTINGS_PAD * 2;
        for (Widget widget : widgets) {
            if (widget.isVisible()) h += widget.height();
        }
        return h;
    }

    float height() {
        return (HEIGHT + settingsHeight() * expand.get()) * searchFade.get();
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
        hover.animateTo(hovered ? 1 : 0);
        if (hovered) {
            ctx.tooltip(module.description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        float on = enabled.get();
        float h = hover.get();

        if (on > 0.001f) {
            r.gradientH(x, y, width, HEIGHT, theme.accent(Math.round(56 * on)), Colors.withAlpha(theme.accentSecondary(), Math.round(10 * on)));
            float barH = (HEIGHT - 6f) * on;
            r.roundedRect(x + 2f, y + (HEIGHT - barH) / 2f, 2f, barH, 1f, theme.accent());
        }
        r.rect(x, y, width, HEIGHT, Colors.fade(Theme.HOVER, h));

        int nameColor = Colors.lerp(Colors.lerp(Theme.TEXT_DIM, 0xFFC8C8D2, h), Theme.TEXT, on);
        r.text(r.ellipsize(module.name(), width - 28f, false), x + 8f + 1.5f * on, Widgets.textY(y, HEIGHT), nameColor);

        float e = expand.get();
        r.chevron(x + width - 9f, y + HEIGHT / 2f, 4.5f, (float) (Math.PI / 2 * e), 1.1f,
            Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, Math.max(h, e)));

        if (e > 0.001f) {
            float settingsY = y + HEIGHT;
            float visible = settingsHeight() * e;
            r.pushScissor(x, settingsY, width, visible);
            r.rect(x, settingsY, width, visible, Theme.SETTINGS_BG);
            r.gradientV(x, settingsY, width, 4f, 0x40000000, 0x00000000);
            r.rect(x + 3f, settingsY + SETTINGS_PAD, 1f, Math.max(0, visible - SETTINGS_PAD * 2), theme.accent(Math.round(90 * e)));
            r.pushAlpha(Math.min(1f, e * 1.4f));

            // Widgets below the clipped area still get laid out, but must not claim hover.
            float clipBottom = settingsY + visible;
            float realMouseY = ctx.mouseY;
            float wy = settingsY + SETTINGS_PAD;
            for (Widget widget : widgets) {
                if (!widget.isVisible()) continue;
                if (ctx.mouseY >= clipBottom) ctx.mouseY = Float.MAX_VALUE;
                widget.render(ctx, x + 3f, wy, width - 3f);
                ctx.mouseY = realMouseY;
                wy += widget.height();
            }
            r.popAlpha();
            r.popScissor();
        }
        r.popAlpha();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchFade.target() == 0) return false;
        if (mouseX < x || mouseX >= x + width) return false;
        if (mouseY >= y && mouseY < y + HEIGHT) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) module.toggle();
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) expanded = !expanded;
            else return false;
            return true;
        }
        if (!expanded || mouseY < y + HEIGHT || mouseY >= y + HEIGHT + settingsHeight() * expand.get()) return false;
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
