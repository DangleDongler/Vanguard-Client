package dev.vanguard.gui.clickgui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widgets;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A draggable, collapsible, scrollable column of modules for one category. */
final class Panel {
    static final float WIDTH = 120f;
    static final float HEADER = 20f;
    private static final float RADIUS = 6f;
    private static final float BOTTOM_PAD = 4f;
    private static final float SCREEN_MARGIN = 6f;

    private final Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final Animation open = new Animation(1, 280, Easing.QUINT_OUT);
    private final Animation scroll = new Animation(0, 200, Easing.CUBIC_OUT);
    private final Animation headerHover = new Animation(0, 140, Easing.LINEAR);
    private final Animation searchDim = new Animation(1, 200, Easing.CUBIC_OUT);

    private float x;
    private float y;
    private boolean expanded = true;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private float scrollTarget;
    private float maxBodyHeight = Float.MAX_VALUE;

    Panel(Category category, List<Module> modules, float x, float y) {
        this.category = category;
        this.x = x;
        this.y = y;
        for (Module module : modules) buttons.add(new ModuleButton(module));
    }

    Category category() {
        return category;
    }

    void moveTo(float x, float y) {
        this.x = x;
        this.y = y;
    }

    boolean isDragging() {
        return dragging;
    }

    private float contentHeight() {
        float h = BOTTOM_PAD;
        for (ModuleButton button : buttons) h += button.height();
        return h;
    }

    private float bodyHeight() {
        return Math.min(contentHeight(), maxBodyHeight) * open.get();
    }

    /** Height with every module collapsed, used for the default layout. */
    float collapsedHeight() {
        return HEADER + buttons.size() * ModuleButton.HEIGHT + BOTTOM_PAD;
    }

    float totalHeight() {
        return HEADER + bodyHeight();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + totalHeight();
    }

    private boolean inHeader(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEADER;
    }

    void render(GuiContext ctx, float screenWidth, float screenHeight, String query) {
        Render2D r = ctx.render;
        Theme theme = ctx.theme;

        if (dragging) {
            x = ctx.rawMouseX - dragOffsetX;
            y = ctx.rawMouseY - dragOffsetY;
        }
        clampToScreen(screenWidth, screenHeight);
        maxBodyHeight = Math.max(40f, screenHeight - SCREEN_MARGIN - y - HEADER);

        int matches = 0;
        for (ModuleButton button : buttons) {
            button.updateSearch(query);
            if (button.matches(query)) matches++;
        }
        searchDim.animateTo(query.isEmpty() || matches > 0 ? 1f : 0.35f);
        open.animateTo(expanded ? 1 : 0);

        float maxScroll = Math.max(0, contentHeight() - maxBodyHeight);
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);

        float body = bodyHeight();
        float total = HEADER + body;
        float o = open.get();

        r.pushAlpha(searchDim.get());
        r.shadow(x, y, WIDTH, total, RADIUS, 14f, Theme.SHADOW);
        r.roundedRect(x, y, WIDTH, total, RADIUS, Theme.PANEL);

        // Header: square bottom corners while the body is showing.
        float bottomRadius = RADIUS * (1f - Math.min(1f, o * 4f));
        r.roundedRect(x, y, WIDTH, HEADER, RADIUS, RADIUS, bottomRadius, bottomRadius, Theme.HEADER);
        boolean headerHovered = ctx.hovered(x, y, WIDTH, HEADER);
        headerHover.animateTo(headerHovered || dragging ? 1 : 0);
        if (headerHovered || dragging) ctx.cursor(dragging ? CursorTypes.RESIZE_ALL : CursorTypes.POINTING_HAND);
        r.roundedRect(x, y, WIDTH, HEADER, RADIUS, RADIUS, bottomRadius, bottomRadius, Colors.fade(Theme.HOVER, headerHover.get()));

        float titleY = Widgets.textY(y, HEADER);
        r.roundedRect(x + 8f, y + HEADER / 2f - 3f, 2.5f, 6f, 1.25f, theme.accent());
        r.text(category.displayName(), x + 14f, titleY, Theme.TEXT, true);
        String count = String.valueOf(query.isEmpty() ? buttons.size() : matches);
        r.text(count, x + WIDTH - 20f - r.textWidth(count), titleY, Theme.TEXT_MUTED);
        r.chevron(x + WIDTH - 10f, y + HEADER / 2f, 4.5f, (float) (Math.PI / 2 * o), 1.1f,
            Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, headerHover.get()));

        if (body > 0.5f) {
            r.gradientH(x, y + HEADER - 0.75f, WIDTH, 0.75f, theme.accent(Math.round(200 * o)), theme.accentSecondary() & 0x00FFFFFF);

            float bodyY = y + HEADER;
            r.pushScissor(x, bodyY, WIDTH, body);
            float realMouseY = ctx.mouseY;
            boolean mouseInBody = ctx.mouseY >= bodyY && ctx.mouseY < bodyY + body;
            if (!mouseInBody) ctx.mouseY = Float.MAX_VALUE;
            float by = bodyY - scroll.get();
            for (ModuleButton button : buttons) {
                float h = button.height();
                // Rows scrolled out of view are only laid out, so their click bounds stay current.
                if (by + h >= bodyY && by <= bodyY + body) button.render(ctx, x, by, WIDTH);
                else button.layout(x, by, WIDTH);
                by += h;
            }
            ctx.mouseY = realMouseY;
            r.popScissor();

            if (maxScroll > 0) drawScrollbar(r, theme, bodyY, body, maxScroll);
        }

        r.roundedOutline(x, y, WIDTH, total, RADIUS, 0.6f, Theme.OUTLINE);
        r.popAlpha();
    }

    private void drawScrollbar(Render2D r, Theme theme, float bodyY, float body, float maxScroll) {
        float trackH = body - BOTTOM_PAD - 4f;
        float thumbH = Math.max(12f, trackH * (body / (body + maxScroll)));
        float thumbY = bodyY + 2f + (trackH - thumbH) * (scroll.get() / maxScroll);
        r.roundedRect(x + WIDTH - 3f, thumbY, 1.5f, thumbH, 0.75f, theme.accent(110));
    }

    private void clampToScreen(float screenWidth, float screenHeight) {
        x = Math.clamp(x, SCREEN_MARGIN - WIDTH + 30f, Math.max(SCREEN_MARGIN, screenWidth - 30f));
        y = Math.clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - HEADER - SCREEN_MARGIN));
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inHeader(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                dragging = true;
                dragOffsetX = (float) mouseX - x;
                dragOffsetY = (float) mouseY - y;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                expanded = !expanded;
            }
            return true;
        }
        if (!contains(mouseX, mouseY)) return false;
        // Rows can extend past the visible body when scrolled; only the visible part is clickable.
        if (mouseY >= y + HEADER + bodyHeight()) return true;
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true;
    }

    void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        for (ModuleButton moduleButton : buttons) moduleButton.mouseReleased(mouseX, mouseY, button);
    }

    void mouseScrolled(double amount) {
        scrollTarget -= (float) amount * 24f;
    }

    void clickedOutside() {
        for (ModuleButton button : buttons) button.clickedOutside();
    }

    boolean keyPressed(int key, int modifiers) {
        for (ModuleButton button : buttons) {
            if (button.keyPressed(key, modifiers)) return true;
        }
        return false;
    }

    boolean isCapturingKeyboard() {
        for (ModuleButton button : buttons) {
            if (button.isCapturingKeyboard()) return true;
        }
        return false;
    }

    JsonObject save() {
        JsonObject json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("expanded", expanded);
        JsonObject expandedModules = new JsonObject();
        for (ModuleButton button : buttons) {
            if (button.isExpanded()) expandedModules.addProperty(button.module().name(), true);
        }
        json.add("expandedModules", expandedModules);
        return json;
    }

    void load(JsonObject json) {
        try {
            if (json.has("x")) x = json.get("x").getAsFloat();
            if (json.has("y")) y = json.get("y").getAsFloat();
            if (json.has("expanded")) {
                expanded = json.get("expanded").getAsBoolean();
                open.snap(expanded ? 1 : 0);
            }
            if (json.get("expandedModules") instanceof JsonObject expandedModules) {
                for (ModuleButton button : buttons) button.setExpanded(expandedModules.has(button.module().name()));
            }
        } catch (RuntimeException ignored) {
            // Malformed GUI state only costs the saved layout.
        }
    }
}
