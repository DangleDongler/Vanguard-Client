package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.anim.Spring;
import dev.vanguard.gui.clickgui.widget.Controls;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Module;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The left pane: the open category's modules, or the search results. Each row shows the module's
 * icon, name, what it does and a switch. Click a row to open its settings, click its switch or
 * right-click it to turn it on or off. The pane grows and shrinks with its list.
 */
final class ModuleList {
    static final float WIDTH = 190f;
    private static final float RADIUS = 18f;
    private static final float HEADER = 46f;
    private static final float ROW = 36f;
    private static final float PAD_BOTTOM = 7f;
    private static final float EMPTY_HEIGHT = 34f;
    private static final float TILE = 23f;
    private static final float SWITCH_W = 19f;
    private static final float SWITCH_H = 11f;

    /** What the list needs from the screen. */
    interface Host {
        boolean isSelected(Module module);

        void select(Module module);
    }

    private final Droplet droplet = new Droplet();
    private final Spring height = new Spring(120, 260, 30);
    private final Animation scroll = new Animation(0, 220, Easing.CUBIC_OUT);
    private final Map<Module, Animation> hover = new IdentityHashMap<>();
    private final Map<Module, Animation> enabled = new IdentityHashMap<>();
    private float scrollTarget;
    private float maxScroll;

    // Layout from the last frame, for clicks.
    private List<Module> shown = List.of();
    private float x, y, h;

    void resetScroll() {
        scrollTarget = 0;
    }

    void render(GuiContext ctx, float x, float y, float maxHeight, String title, String caption,
                List<Module> modules, Host host, float materialize) {
        Render2D r = ctx.render;
        this.x = x;
        this.y = y;
        this.shown = modules;

        float content = modules.isEmpty() ? EMPTY_HEIGHT : modules.size() * ROW;
        height.setTarget(Math.min(maxHeight, HEADER + content + PAD_BOTTOM));
        h = Math.min(maxHeight, height.update());
        ctx.glass(ctx.theme.pane, x, y, WIDTH, h, RADIUS, materialize);

        r.title(title, x + 15f, y + 13f, Theme.TEXT);
        r.small(caption, x + 15f, y + 29f, Theme.TEXT_MUTED);

        float top = y + HEADER - 4f, bottom = y + h - 4f;
        maxScroll = Math.max(0, HEADER + content + PAD_BOTTOM - h);
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);
        float listY = y + HEADER - scroll.get();

        float mouseY = ctx.mouseY;
        if (mouseY < top || mouseY >= bottom) ctx.mouseY = -1e6f;
        r.pushScissor(x, top, WIDTH, bottom - top);

        // The drop behind the selected row.
        int selected = -1;
        for (int i = 0; i < modules.size(); i++) {
            if (host.isSelected(modules.get(i))) selected = i;
        }
        if (selected >= 0) droplet.moveTo(listY + selected * ROW + ROW / 2f, ROW - 3f);
        droplet.show(selected >= 0);
        droplet.update();
        float drop = droplet.visibility();
        if (drop > 0.001f) {
            float w = (WIDTH - 12f) * (0.94f + 0.06f * droplet.thickness);
            r.pushAlpha(drop);
            ctx.glass(ctx.theme.droplet, x + (WIDTH - w) / 2f, droplet.start, w, droplet.end - droplet.start, 12f, materialize * drop);
            r.popAlpha();
        }

        if (modules.isEmpty()) {
            r.small("No modules match.", x + 15f, r.smallY(listY, EMPTY_HEIGHT), Theme.TEXT_MUTED);
        }
        for (int i = 0; i < modules.size(); i++) {
            float rowY = listY + i * ROW;
            if (rowY + ROW < top || rowY > bottom) continue;
            drawRow(ctx, r, modules.get(i), rowY, i == selected);
        }
        r.popScissor();
        ctx.mouseY = mouseY;
    }

    private void drawRow(GuiContext ctx, Render2D r, Module module, float rowY, boolean selected) {
        boolean over = ctx.hovered(x + 6f, rowY, WIDTH - 12f, ROW);
        boolean switchable = module.persistsEnabledState();
        if (over) {
            ctx.cursor(CursorTypes.POINTING_HAND);
            ctx.tooltip(switchable ? module.description() + " Right-click to turn it on or off." : module.description());
        }
        Animation hovered = hover.computeIfAbsent(module, m -> new Animation(0, 140, Easing.LINEAR));
        hovered.animateTo(over && !selected ? 1 : 0);
        Animation on = enabled.computeIfAbsent(module, m -> new Animation(m.isEnabled() ? 1 : 0, 260, Easing.CUBIC_OUT));
        on.animateTo(switchable && module.isEnabled() ? 1 : 0);
        float hv = hovered.get(), lit = on.get();

        if (hv > 0.001f) r.roundedRect(x + 6f, rowY + 1.5f, WIDTH - 12f, ROW - 3f, 12f, Colors.fade(Theme.HOVER, hv));

        // Icon tile: filled with the accent while the module is on.
        float tx = x + 13f, ty = rowY + (ROW - TILE) / 2f;
        r.roundedRect(tx, ty, TILE, TILE, 8f, Theme.TILE);
        if (lit > 0.001f) {
            r.pushAlpha(lit);
            r.shadow(tx, ty, TILE, TILE, 8f, 6f, ctx.theme.accent(70));
            r.roundedGradientV(tx, ty, TILE, TILE, 8f, ctx.theme.accent(), ctx.theme.accentDeep());
            r.popAlpha();
        }
        Icons.module(r, module, tx + TILE / 2f, ty + TILE / 2f, 11f, Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, Math.max(lit, selected ? 1f : 0f)));

        float textX = tx + TILE + 9f;
        float textW = x + WIDTH - 14f - (switchable ? SWITCH_W + 8f : 0f) - textX;
        r.smallBold(r.ellipsizeSmall(module.name(), textW, true), textX, rowY + 8.5f, Theme.TEXT);
        r.small(r.ellipsizeSmall(module.description(), textW), textX, rowY + 19.5f, selected ? Theme.TEXT_DIM : Theme.TEXT_MUTED);

        if (switchable) {
            float sx = x + WIDTH - 14f - SWITCH_W, sy = rowY + (ROW - SWITCH_H) / 2f;
            Controls.drawSwitch(ctx, sx, sy, SWITCH_W, SWITCH_H, lit, ctx.hovered(sx - 3f, sy - 3f, SWITCH_W + 6f, SWITCH_H + 6f) ? 1f : 0f);
        }
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + h;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
        if (!contains(mouseX, mouseY)) return false;
        float listY = y + HEADER - scroll.get();
        if (mouseY < y + HEADER - 4f) return true;
        int index = (int) Math.floor((mouseY - listY) / ROW);
        if (index < 0 || index >= shown.size()) return true;
        Module module = shown.get(index);
        boolean onSwitch = mouseX >= x + WIDTH - 20f - SWITCH_W;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && onSwitch && module.persistsEnabledState())) {
            if (module.persistsEnabledState()) module.toggle();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            host.select(module);
        }
        return true;
    }

    void mouseScrolled(double amount) {
        scrollTarget -= (float) amount * 24f;
    }
}
