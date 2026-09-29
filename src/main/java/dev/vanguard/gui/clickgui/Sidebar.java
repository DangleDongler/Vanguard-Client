package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The left column: the logo, each category with its modules underneath (click a category to fold
 * it), and Configs at the bottom. Click a module to open its page; right-click to turn it on or off.
 */
final class Sidebar {
    static final float WIDTH = 136f;
    private static final float RADIUS = 7f;
    private static final float CAP_HEIGHT = 42f;
    private static final float LIST_TOP = CAP_HEIGHT + 22f;
    private static final float CATEGORY_HEIGHT = 26f;
    private static final float MODULE_HEIGHT = 18f;
    private static final float FOOTER_HEIGHT = 36f;
    private static final float ICON_X = 16f;
    private static final float LABEL_X = 26f;

    /** What the sidebar needs from the screen. */
    interface Host {
        boolean isSelected(Module module);

        boolean configsSelected();

        void select(Module module);

        void selectConfigs();
    }

    private final List<Group> groups = new ArrayList<>();
    private final Animation footerHover = new Animation(0, 130, Easing.LINEAR);
    private final Animation scroll = new Animation(0, 200, Easing.CUBIC_OUT);
    private float scrollTarget;
    private float maxScroll;
    private float x, y, height;

    Sidebar(List<Category> categories, java.util.function.Function<Category, List<Module>> modules) {
        for (Category category : categories) groups.add(new Group(category, modules.apply(category)));
    }

    /** Categories folded open, for saving between sessions. */
    List<Category> expanded() {
        List<Category> open = new ArrayList<>();
        for (Group group : groups) {
            if (group.expanded) open.add(group.category);
        }
        return open;
    }

    void setExpanded(Set<Category> open) {
        for (Group group : groups) {
            group.expanded = open.contains(group.category);
            group.open.snap(group.expanded ? 1 : 0);
        }
    }

    /** Opens the category holding {@code module}, so the selected module is always in view. */
    void reveal(Module module) {
        for (Group group : groups) {
            if (group.modules.contains(module)) group.expanded = true;
        }
    }

    private float listBottom() {
        return y + height - FOOTER_HEIGHT;
    }

    void render(GuiContext ctx, float x, float y, float height, Host host) {
        this.x = x;
        this.y = y;
        this.height = height;
        Render2D r = ctx.render;

        // A frame with a lighter cap for the logo, and the list in an inset panel below it.
        r.shadow(x, y, WIDTH, height, RADIUS, 14f, Theme.SHADOW);
        r.roundedRect(x, y, WIDTH, height, RADIUS, Theme.SIDEBAR_FRAME);
        r.roundedGradientV(x, y, WIDTH, CAP_HEIGHT + RADIUS, RADIUS, Theme.SIDEBAR_CAP_TOP, Theme.SIDEBAR_FRAME);
        r.roundedOutline(x, y, WIDTH, height, RADIUS, 0.6f, Theme.OUTLINE);
        float inset = 2f;
        r.roundedRect(x + inset, y + CAP_HEIGHT, WIDTH - inset * 2, height - CAP_HEIGHT - inset, RADIUS - 1.5f, Theme.SIDEBAR);
        r.roundedOutline(x + inset, y + CAP_HEIGHT, WIDTH - inset * 2, height - CAP_HEIGHT - inset, RADIUS - 1.5f, 0.6f, Theme.OUTLINE);
        Icons.logo(r, x + WIDTH / 2f, y + CAP_HEIGHT / 2f, 20f, 0xFFF2F2F2);

        r.tiny("CATEGORIES", x + 9f, y + CAP_HEIGHT + 9f, Theme.CAPTION);

        // Categories and their modules, scrolling if they don't fit.
        float top = y + LIST_TOP, bottom = listBottom();
        float contentHeight = 0;
        for (Group group : groups) contentHeight += group.height();
        maxScroll = Math.max(0, contentHeight - (bottom - top));
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);

        float mouseY = ctx.mouseY;
        if (mouseY < top || mouseY >= bottom) ctx.mouseY = -1e6f;
        r.pushScissor(x, top, WIDTH, bottom - top);
        float cy = top - scroll.get();
        for (Group group : groups) {
            group.render(ctx, x, cy, host);
            cy += group.height();
        }
        r.popScissor();
        ctx.mouseY = mouseY;

        drawFooter(ctx, host);
    }

    private void drawFooter(GuiContext ctx, Host host) {
        Render2D r = ctx.render;
        float fy = listBottom();
        r.rect(x + 2f, fy, WIDTH - 4f, 0.6f, Theme.SEPARATOR);
        float rowY = fy + (FOOTER_HEIGHT - CATEGORY_HEIGHT) / 2f;
        boolean hovered = ctx.hovered(x, rowY, WIDTH, CATEGORY_HEIGHT);
        if (hovered) ctx.cursor(CursorTypes.POINTING_HAND);
        footerHover.animateTo(hovered ? 1 : 0);
        boolean selected = host.configsSelected();
        if (selected) r.roundedRect(x + 6f, rowY + 3f, WIDTH - 12f, CATEGORY_HEIGHT - 6f, 4f, Theme.SELECTED);
        int color = selected ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, footerHover.get());
        r.smallBold("Configs", x + 12f, r.smallY(rowY, CATEGORY_HEIGHT), color);
        Icons.file(r, x + WIDTH - ICON_X, rowY + CATEGORY_HEIGHT / 2f, 9f, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, footerHover.get()));
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
        if (!contains(mouseX, mouseY)) return false;
        float fy = listBottom();
        if (mouseY >= fy) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) host.selectConfigs();
            return true;
        }
        if (mouseY < y + LIST_TOP) return true;
        for (Group group : groups) {
            if (group.mouseClicked(mouseX, mouseY, button, host)) return true;
        }
        return true;
    }

    void mouseScrolled(double amount) {
        scrollTarget -= (float) amount * 24f;
    }

    /** A category row and, below it, its modules while it's open. */
    private static final class Group {
        final Category category;
        final List<Module> modules;
        final Animation open = new Animation(0, 260, Easing.QUINT_OUT);
        final Animation hover = new Animation(0, 130, Easing.LINEAR);
        final Animation[] moduleHover;
        boolean expanded;
        float rowY;

        Group(Category category, List<Module> modules) {
            this.category = category;
            this.modules = modules;
            this.moduleHover = new Animation[modules.size()];
            for (int i = 0; i < moduleHover.length; i++) moduleHover[i] = new Animation(0, 130, Easing.LINEAR);
        }

        float modulesHeight() {
            return (modules.size() * MODULE_HEIGHT + 4f) * open.get();
        }

        float height() {
            open.animateTo(expanded ? 1 : 0);
            return CATEGORY_HEIGHT + modulesHeight();
        }

        void render(GuiContext ctx, float x, float y, Host host) {
            Render2D r = ctx.render;
            this.rowY = y;
            boolean hovered = ctx.hovered(x, y, WIDTH, CATEGORY_HEIGHT);
            if (hovered) ctx.cursor(CursorTypes.POINTING_HAND);
            hover.animateTo(hovered ? 1 : 0);
            float h = hover.get(), o = open.get();

            int iconColor = Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, Math.max(h, o));
            Icons.category(r, category, x + ICON_X, y + CATEGORY_HEIGHT / 2f, 9f, iconColor);
            r.smallBold(category.displayName(), x + LABEL_X, r.smallY(y, CATEGORY_HEIGHT), Colors.lerp(0xFFD4D4D4, 0xFFF0F0F0, Math.max(h, o)));
            float angle = (float) (Math.PI / 2 - Math.PI * o);
            r.chevron(x + WIDTH - ICON_X, y + CATEGORY_HEIGHT / 2f, 4.2f, angle, 1f, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, h));

            float listHeight = modulesHeight();
            if (listHeight <= 0.01f) return;
            float listY = y + CATEGORY_HEIGHT;
            float mouseY = ctx.mouseY;
            if (mouseY >= listY + listHeight) ctx.mouseY = -1e6f;
            r.pushScissor(x, listY, WIDTH, listHeight);
            r.pushAlpha(o);
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                float my = listY + i * MODULE_HEIGHT;
                boolean over = ctx.hovered(x, my, WIDTH, MODULE_HEIGHT);
                if (over) {
                    ctx.cursor(CursorTypes.POINTING_HAND);
                    ctx.tooltip(module.persistsEnabledState() ? module.description() + " Right-click to turn it on or off." : module.description());
                }
                moduleHover[i].animateTo(over ? 1 : 0);
                float mh = moduleHover[i].get();

                boolean selected = host.isSelected(module);
                if (selected) r.roundedRect(x + 6f, my + 1f, WIDTH - 12f, MODULE_HEIGHT - 2f, 4f, Theme.SELECTED);
                else if (mh > 0.001f) r.roundedRect(x + 6f, my + 1f, WIDTH - 12f, MODULE_HEIGHT - 2f, 4f, Colors.fade(Theme.HOVER, mh));

                boolean on = module.isEnabled() && module.persistsEnabledState();
                int color = selected || on ? Theme.TEXT : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, mh);
                r.small(module.name(), x + LABEL_X, r.smallY(my, MODULE_HEIGHT), color);
                if (on) r.circle(x + WIDTH - ICON_X, my + MODULE_HEIGHT / 2f, 1.7f, ctx.theme.accent());
            }
            r.popAlpha();
            r.popScissor();
            ctx.mouseY = mouseY;
        }

        boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
            if (mouseY >= rowY && mouseY < rowY + CATEGORY_HEIGHT) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) expanded = !expanded;
                return true;
            }
            float listY = rowY + CATEGORY_HEIGHT;
            if (!expanded || mouseY < listY || mouseY >= listY + modulesHeight()) return false;
            int index = (int) ((mouseY - listY) / MODULE_HEIGHT);
            if (index < 0 || index >= modules.size()) return true;
            Module module = modules.get(index);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) host.select(module);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && module.persistsEnabledState()) module.toggle();
            return true;
        }
    }
}
