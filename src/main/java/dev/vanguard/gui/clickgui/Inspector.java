package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.anim.Spring;
import dev.vanguard.gui.clickgui.widget.Controls;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Module;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The right pane: the open page. A header with the page's icon, name and description (and the
 * module's switch), then its sections, scrolling under the header when they don't fit. The pane
 * grows and shrinks to fit each page.
 */
final class Inspector {
    private static final float RADIUS = 20f;
    private static final float HEADER = 64f;
    private static final float SIDE = 10f;
    private static final float SECTION_GAP = 8f;
    private static final float PAD_BOTTOM = 10f;
    private static final float MIN_HEIGHT = 150f;
    private static final float TILE = 30f;
    private static final float SWITCH_W = 30f;
    private static final float SWITCH_H = 17f;

    private final Spring height = new Spring(MIN_HEIGHT, 240, 29);
    private final Animation scroll = new Animation(0, 240, Easing.CUBIC_OUT);
    private final Animation switchState = new Animation(0, 280, Easing.CUBIC_OUT);
    private final Animation switchHover = new Animation(0, 120, Easing.LINEAR);
    private Page shownPage;
    private float scrollTarget;
    private float maxScroll;

    // Layout from the last frame, for clicks.
    private float x, y, w, h;
    private float switchX, switchY;

    void resetScroll() {
        scrollTarget = 0;
        scroll.snap(0);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private float contentTop() {
        return y + HEADER;
    }

    private float contentBottom() {
        return y + h - 6f;
    }

    void render(GuiContext ctx, float x, float y, float w, float maxHeight, Page page, float pageProgress, float materialize) {
        Render2D r = ctx.render;
        this.x = x;
        this.y = y;
        this.w = w;
        if (page != shownPage) {
            shownPage = page;
            Module module = page.module();
            switchState.snap(module != null && module.isEnabled() ? 1 : 0);
        }

        List<Section> sections = page.sections();
        float content = 0;
        for (Section section : sections) content += section.height() + SECTION_GAP;
        content = Math.max(0, content - SECTION_GAP);
        height.setTarget(Math.clamp(HEADER + content + PAD_BOTTOM, Math.min(MIN_HEIGHT, maxHeight), maxHeight));
        h = Math.min(maxHeight, height.update());
        maxScroll = Math.max(0, HEADER + content + PAD_BOTTOM - h);
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);

        ctx.glass(ctx.theme.pane, x, y, w, h, RADIUS, materialize);

        r.pushAlpha(pageProgress);
        drawHeader(ctx, r, page, pageProgress);
        drawSections(ctx, r, sections, pageProgress);
        r.popAlpha();
    }

    private void drawHeader(GuiContext ctx, Render2D r, Page page, float pageProgress) {
        Module module = page.module();
        boolean switchable = module != null && module.persistsEnabledState();
        switchState.animateTo(switchable && module.isEnabled() ? 1 : 0);
        float on = switchState.get();

        float tx = x + 16f, ty = y + 17f;
        r.roundedRect(tx, ty, TILE, TILE, 10f, Theme.TILE);
        if (on > 0.001f) {
            r.pushAlpha(on);
            r.shadow(tx, ty, TILE, TILE, 10f, 9f, ctx.theme.accent(80));
            r.roundedGradientV(tx, ty, TILE, TILE, 10f, ctx.theme.accent(), ctx.theme.accentDeep());
            r.popAlpha();
        }
        page.icon(r, tx + TILE / 2f, ty + TILE / 2f, 14f, Colors.lerp(Theme.TEXT, 0xFFFFFFFF, on));

        float textX = tx + TILE + 11f;
        float textW = x + w - 16f - (switchable ? SWITCH_W + 12f : 0f) - textX;
        float rise = 4f * (1f - pageProgress);
        r.title(page.title(), textX, y + 16f + rise, Theme.TEXT);
        List<String> lines = r.wrapSmall(page.description(), textW);
        float ly = y + 32f + rise;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = lines.get(i);
            if (i == 1 && lines.size() > 2) line = r.ellipsizeSmall(line + "…", textW);
            r.small(line, textX, ly, Theme.TEXT_DIM);
            ly += 9.5f;
        }

        if (switchable) {
            switchX = x + w - 16f - SWITCH_W;
            switchY = y + 23.5f;
            boolean over = ctx.hovered(switchX - 4f, switchY - 4f, SWITCH_W + 8f, SWITCH_H + 8f);
            if (over) {
                ctx.cursor(CursorTypes.POINTING_HAND);
                ctx.tooltip("Turn " + module.name() + " " + (module.isEnabled() ? "off." : "on."));
            }
            switchHover.animateTo(over ? 1 : 0);
            Controls.drawSwitch(ctx, switchX, switchY, SWITCH_W, SWITCH_H, on, switchHover.get());
        } else {
            switchX = Float.NaN;
        }

        // A hairline under the header once the settings scroll beneath it.
        float divider = Math.clamp(scroll.get() / 12f, 0f, 1f);
        if (divider > 0.001f) r.rect(x + 14f, contentTop() - 3f, w - 28f, 0.5f, Colors.fade(Theme.HAIRLINE, divider));
    }

    private void drawSections(GuiContext ctx, Render2D r, List<Section> sections, float pageProgress) {
        float top = contentTop() - 3f, bottom = contentBottom();
        float mouseY = ctx.mouseY;
        if (mouseY < top || mouseY >= bottom) ctx.mouseY = -1e6f;
        r.pushScissor(x, top, w, bottom - top);
        float cy = contentTop() - scroll.get() + 8f * (1f - pageProgress);
        for (Section section : sections) {
            float sh = section.height();
            if (cy + sh >= top && cy <= bottom) section.render(ctx, x + SIDE, cy, w - SIDE * 2, top, bottom);
            cy += sh + SECTION_GAP;
        }
        r.popScissor();
        ctx.mouseY = mouseY;

        if (maxScroll > 0) {
            float track = bottom - top - 8f;
            float thumb = Math.max(18f, track * track / (track + maxScroll));
            float thumbY = top + 4f + (track - thumb) * (scroll.get() / maxScroll);
            r.roundedRect(x + w - 5f, thumbY, 2.2f, thumb, 1.1f, 0x40FFFFFF);
        }
    }

    /** Returns whether a control took the click. */
    boolean mouseClicked(double mouseX, double mouseY, int button, Page page) {
        if (!contains(mouseX, mouseY)) return false;
        Module module = page.module();
        if (!Float.isNaN(switchX) && module != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
            && mouseX >= switchX - 4f && mouseX < switchX + SWITCH_W + 4f && mouseY >= switchY - 4f && mouseY < switchY + SWITCH_H + 4f) {
            module.toggle();
            return true;
        }
        if (mouseY >= contentTop() - 3f && mouseY < contentBottom()) {
            for (Section section : page.sections()) {
                if (section.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    void mouseScrolled(double amount) {
        scrollTarget -= (float) amount * 30f;
    }
}
