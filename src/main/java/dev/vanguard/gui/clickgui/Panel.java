package dev.vanguard.gui.clickgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

/**
 * One category's modules. The screen decides where panels go; a panel only animates
 * towards the spot it's given and in or out of existence.
 */
final class Panel {
    static final float HEADER = 24f;
    private static final float RADIUS = 8f;
    private static final float BOTTOM_PAD = 5f;
    private static final float CLOSE_SIZE = 12f;

    private final Category category;
    private final String title;
    private final List<ModuleButton> buttons = new ArrayList<>();
    /** 0 = closed, 1 = open. Drives the fade, the unroll and the space the panel takes. */
    private final Animation presence = new Animation(0, 280, Easing.QUINT_OUT);
    private Animation move = new Animation(1, 340, Easing.QUINT_OUT);
    private final Animation headerHover = new Animation(0, 140, Easing.LINEAR);
    private final Animation closeHover = new Animation(0, 120, Easing.LINEAR);

    private float x, y, width;
    private float fromX, fromY;
    private int column = -1;

    Panel(Category category, String title, List<Module> modules) {
        this.category = category;
        this.title = title;
        for (Module module : modules) buttons.add(new ModuleButton(module));
    }

    Category category() {
        return category;
    }

    void show(boolean shown, long delayMs) {
        presence.animateTo(shown ? 1 : 0, delayMs);
    }

    void hideInstantly() {
        presence.snap(0);
        column = -1;
    }

    /** 0..1 while opening or closing. */
    float presence() {
        return presence.get();
    }

    /** Still taking up space: open, or closing. */
    boolean isPresent() {
        return presence.target() > 0 || presence.get() > 0.001f;
    }

    boolean isShown() {
        return presence.target() > 0;
    }

    int matches(String query) {
        int count = 0;
        for (ModuleButton button : buttons) {
            if (button.matches(query)) count++;
        }
        return count;
    }

    int enabledCount() {
        int count = 0;
        for (ModuleButton button : buttons) {
            if (button.module().isEnabled()) count++;
        }
        return count;
    }

    private float fullHeight() {
        float h = HEADER + BOTTOM_PAD;
        for (ModuleButton button : buttons) h += button.height();
        return h;
    }

    /** Vertical space in the layout, shrinking to nothing while the panel closes. */
    float layoutHeight() {
        return fullHeight() * presence.get();
    }

    /** Moves the panel towards a layout slot. Changing columns glides instead of jumping. */
    void place(float targetX, float targetY, float width, int column) {
        if (this.column == -1 || presence.get() <= 0.001f) {
            move.snap(1);
            fromX = targetX;
            fromY = targetY;
        } else if (column != this.column || width != this.width) {
            fromX = x;
            fromY = y;
            move = new Animation(0, 340, Easing.QUINT_OUT);
            move.animateTo(1);
        }
        this.column = column;
        this.width = width;
        float t = move.get();
        x = fromX + (targetX - fromX) * t;
        y = fromY + (targetY - fromY) * t;
    }

    boolean contains(double mouseX, double mouseY) {
        return isShown() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + layoutHeight();
    }

    void render(GuiContext ctx, String query, boolean countMatches) {
        for (ModuleButton button : buttons) button.updateSearch(query);
        float p = presence.get();
        if (p <= 0.001f) return;

        Render2D r = ctx.render;
        Theme theme = ctx.theme;
        float full = fullHeight();
        float visible = full * p;

        r.pushAlpha(p);
        r.shadow(x, y, width, visible, RADIUS, 14f, Theme.SHADOW);
        r.roundedRect(x, y, width, visible, RADIUS, Theme.PANEL);
        r.pushScissor(x, y, width, visible);

        boolean headerHovered = ctx.hovered(x, y, width, HEADER);
        headerHover.animateTo(headerHovered ? 1 : 0);
        float closeX = x + width - 8f - CLOSE_SIZE, closeY = y + (HEADER - CLOSE_SIZE) / 2f;
        boolean overClose = ctx.hovered(closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
        closeHover.animateTo(overClose ? 1 : 0);
        if (overClose) {
            ctx.cursor(CursorTypes.POINTING_HAND);
            ctx.tooltip("Close tab");
        }

        Icons.category(r, category, x + 15f, y + HEADER / 2f, 9f, theme.accent());
        r.text(title, x + 25f, r.textY(y, HEADER), Theme.TEXT, true);

        // Count on the right, swapped for a close button while the header is hovered.
        float hh = headerHover.get();
        int total = buttons.size();
        String count = countMatches ? matches(query) + " found" : enabledCount() + "/" + total;
        r.pushAlpha(1f - hh);
        r.small(count, x + width - 10f - r.smallWidth(count), r.smallY(y, HEADER), Theme.TEXT_MUTED);
        r.popAlpha();
        if (hh > 0.01f) {
            r.pushAlpha(hh);
            float ch = closeHover.get();
            r.circle(closeX + CLOSE_SIZE / 2f, closeY + CLOSE_SIZE / 2f, CLOSE_SIZE / 2f, Colors.withAlpha(0xFFFFFF, Math.round(24 * ch)));
            Icons.close(r, closeX + CLOSE_SIZE / 2f, closeY + CLOSE_SIZE / 2f, 7f, Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT, ch));
            r.popAlpha();
        }

        // Signature hairline: accent fading out to the right.
        r.gradientH(x + 10f, y + HEADER - 0.6f, width - 20f, 0.6f, theme.accent(170), theme.accentSecondary() & 0x00FFFFFF);

        float bodyY = y + HEADER;
        float realMouseY = ctx.mouseY;
        if (ctx.mouseY < bodyY || ctx.mouseY >= y + visible) ctx.mouseY = Float.MAX_VALUE;
        float by = bodyY;
        for (ModuleButton button : buttons) {
            button.render(ctx, x, by, width);
            by += button.height();
        }
        ctx.mouseY = realMouseY;

        r.popScissor();
        r.roundedOutline(x, y, width, visible, RADIUS, 0.6f, Theme.OUTLINE);
        r.popAlpha();
    }

    /**
     * @param onClose called when the header's close button is clicked
     */
    boolean mouseClicked(double mouseX, double mouseY, int button, Runnable onClose) {
        if (!contains(mouseX, mouseY)) return false;
        if (mouseY < y + HEADER) {
            float closeX = x + width - 8f - CLOSE_SIZE;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= closeX && mouseX < closeX + CLOSE_SIZE) onClose.run();
            return true;
        }
        for (ModuleButton moduleButton : buttons) {
            if (moduleButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true;
    }

    void mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleButton moduleButton : buttons) moduleButton.mouseReleased(mouseX, mouseY, button);
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

    void saveExpanded(JsonArray into) {
        for (ModuleButton button : buttons) {
            if (button.isExpanded()) into.add(button.module().name());
        }
    }

    void loadExpanded(JsonArray names) {
        for (ModuleButton button : buttons) {
            boolean expanded = false;
            for (JsonElement name : names) {
                if (name.isJsonPrimitive() && name.getAsString().equals(button.module().name())) expanded = true;
            }
            button.setExpanded(expanded);
        }
    }
}
