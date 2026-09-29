package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.anim.Spring;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Three glass capsules across the top: the brand on the left, the categories in the middle with a
 * liquid drop marking the open one, and search and configs on the right. Search slides open into a
 * text field; typing anywhere in the menu opens it too.
 */
final class TopBar {
    static final float HEIGHT = 30f;
    private static final float INSET = 3f;
    private static final float TAB_PAD = 12f;
    private static final float ICON = 9f;
    private static final float BUTTON = 30f;
    private static final float SEARCH_WIDTH = 150f;
    private static final int MAX_QUERY = 32;

    /** What the bar needs from the screen. */
    interface Host {
        List<Category> categories();

        Category category();

        void selectCategory(Category category);

        boolean configsOpen();

        void toggleConfigs();

        /** The search text changed. */
        void searchChanged();
    }

    private final Droplet tabDrop = new Droplet();
    private final Map<Category, Animation> tabHover = new EnumMap<>(Category.class);
    private final Animation searchHover = new Animation(0, 120, Easing.LINEAR);
    private final Animation configsHover = new Animation(0, 120, Easing.LINEAR);
    private final Animation configsOn = new Animation(0, 220, Easing.CUBIC_OUT);
    private final Animation focus = new Animation(0, 160, Easing.CUBIC_OUT);
    private final Spring searchWidth = new Spring(0, 320, 32);
    private final StringBuilder query = new StringBuilder();
    private boolean searchOpen;
    private boolean focused;
    private long lastEdit;

    // Layout from the last frame, for clicks.
    private float y;
    private float tabsX;
    private float[] tabStarts = new float[0];
    private float[] tabWidths = new float[0];
    private List<Category> tabCategories = List.of();
    private float actionsX, actionsW;

    // ------------------------------------------------------------ search

    String query() {
        return query.toString().trim();
    }

    boolean searching() {
        return !query().isEmpty();
    }

    boolean isCapturingKeyboard() {
        return focused;
    }

    /** Opens search with {@code codepoint} already typed, for typing anywhere in the menu. */
    void typeToSearch(int codepoint, Host host) {
        searchOpen = true;
        focused = true;
        charTyped(codepoint, host);
    }

    void closeSearch(Host host) {
        boolean had = !query.isEmpty();
        query.setLength(0);
        searchOpen = false;
        focused = false;
        if (had) host.searchChanged();
    }

    void clickedOutside() {
        focused = false;
        if (query.isEmpty()) searchOpen = false;
    }

    boolean keyPressed(int key, int modifiers, Host host) {
        if (!focused) return false;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (query.isEmpty()) closeSearch(host);
                else {
                    query.setLength(0);
                    host.searchChanged();
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused = false;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (control) query.setLength(0);
                else if (!query.isEmpty()) query.deleteCharAt(query.length() - 1);
                host.searchChanged();
            }
            case GLFW.GLFW_KEY_V -> {
                if (!control) return false;
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("\\p{Cntrl}", "");
                query.append(clip, 0, Math.min(clip.length(), MAX_QUERY - query.length()));
                host.searchChanged();
            }
            default -> {
                // Printable keys arrive as characters instead.
                return false;
            }
        }
        lastEdit = System.currentTimeMillis();
        return true;
    }

    boolean charTyped(int codepoint, Host host) {
        if (!focused) return false;
        if (query.length() < MAX_QUERY && !Character.isISOControl(codepoint)) {
            query.appendCodePoint(codepoint);
            lastEdit = System.currentTimeMillis();
            host.searchChanged();
        }
        return true;
    }

    // ------------------------------------------------------------ rendering

    void render(GuiContext ctx, float x, float y, float width, Host host, float materialize) {
        Render2D r = ctx.render;
        this.y = y;
        drawBrand(ctx, r, x, y, materialize);
        drawTabs(ctx, r, x + width / 2f, host, materialize);
        drawActions(ctx, r, x + width, host, materialize);
    }

    private void drawBrand(GuiContext ctx, Render2D r, float x, float y, float materialize) {
        String name = "Vanguard";
        float w = 34f + r.textWidth(name, true) + 13f;
        ctx.glass(ctx.theme.capsule, x, y, w, HEIGHT, HEIGHT / 2f, materialize);
        float cx = x + 17f, cy = y + HEIGHT / 2f;
        // The mark sits on a soft pool of accent light.
        r.shadow(cx - 2f, cy - 2f, 4f, 4f, 2f, 9f, ctx.theme.accent(90));
        Icons.logo(r, cx, cy + 0.3f, 13f, Theme.TEXT);
        r.text(name, x + 31f, r.textY(y, HEIGHT), Theme.TEXT, true);
    }

    private void drawTabs(GuiContext ctx, Render2D r, float centerX, Host host, float materialize) {
        tabCategories = host.categories();
        int count = tabCategories.size();
        if (tabStarts.length != count) {
            tabStarts = new float[count];
            tabWidths = new float[count];
        }
        float total = INSET * 2;
        for (int i = 0; i < count; i++) {
            tabWidths[i] = TAB_PAD * 2 + ICON + 5f + r.smallBoldWidth(tabCategories.get(i).displayName());
            total += tabWidths[i];
        }
        tabsX = centerX - total / 2f;
        ctx.glass(ctx.theme.capsule, tabsX, y, total, HEIGHT, HEIGHT / 2f, materialize);

        float tx = tabsX + INSET;
        int selected = -1;
        for (int i = 0; i < count; i++) {
            tabStarts[i] = tx;
            if (tabCategories.get(i) == host.category()) selected = i;
            tx += tabWidths[i];
        }

        // The drop under the open category, hidden while search results replace the category.
        boolean showDrop = selected >= 0 && !searching();
        if (selected >= 0) tabDrop.moveTo(tabStarts[selected] + tabWidths[selected] / 2f, tabWidths[selected]);
        tabDrop.show(showDrop);
        tabDrop.update();
        float drop = tabDrop.visibility();
        if (drop > 0.001f) {
            float h = (HEIGHT - INSET * 2) * tabDrop.thickness;
            r.pushAlpha(drop);
            ctx.glass(ctx.theme.droplet, tabDrop.start, y + (HEIGHT - h) / 2f, tabDrop.end - tabDrop.start, h, h / 2f, materialize * drop);
            r.popAlpha();
        }

        for (int i = 0; i < count; i++) {
            Category category = tabCategories.get(i);
            boolean over = ctx.hovered(tabStarts[i], y, tabWidths[i], HEIGHT);
            if (over) ctx.cursor(CursorTypes.POINTING_HAND);
            Animation hover = tabHover.computeIfAbsent(category, c -> new Animation(0, 140, Easing.LINEAR));
            hover.animateTo(over ? 1 : 0);
            boolean on = i == selected && showDrop;
            int color = on ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover.get());
            float ix = tabStarts[i] + TAB_PAD + ICON / 2f;
            Icons.category(r, category, ix, y + HEIGHT / 2f, ICON, color);
            r.smallBold(category.displayName(), ix + ICON / 2f + 5f, r.smallY(y, HEIGHT), color);
        }
    }

    private void drawActions(GuiContext ctx, Render2D r, float right, Host host, float materialize) {
        searchWidth.setTarget(searchOpen ? SEARCH_WIDTH : 0f);
        float extra = Math.max(0f, searchWidth.update());
        actionsW = BUTTON * 2 + extra;
        actionsX = right - actionsW;
        ctx.glass(ctx.theme.capsule, actionsX, y, actionsW, HEIGHT, HEIGHT / 2f, materialize);

        // Search: the magnifier, then the field sliding out of it.
        boolean overSearch = ctx.hovered(actionsX, y, BUTTON + extra, HEIGHT);
        if (overSearch) {
            ctx.cursor(extra > 1f ? CursorTypes.IBEAM : CursorTypes.POINTING_HAND);
            if (!searchOpen) ctx.tooltip("Search modules. You can also just start typing.");
        }
        searchHover.animateTo(overSearch ? 1 : 0);
        focus.animateTo(focused ? 1 : 0);
        Icons.search(r, actionsX + BUTTON / 2f + 1f, y + HEIGHT / 2f, 10f, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(searchHover.get(), focus.get())));
        if (extra > 1f) {
            float fieldX = actionsX + BUTTON - 3f, fieldW = extra - 2f;
            r.pushScissor(fieldX, y, fieldW, HEIGHT);
            float textY = r.smallY(y, HEIGHT);
            if (query.isEmpty()) {
                r.small("Search modules", fieldX, textY, Theme.TEXT_MUTED);
                drawCaret(r, fieldX, textY);
            } else {
                String shown = query.toString();
                while (!shown.isEmpty() && r.smallWidth(shown) > fieldW - 8f) shown = shown.substring(1);
                float drawn = r.small(shown, fieldX, textY, Theme.TEXT);
                drawCaret(r, fieldX + drawn + 1f, textY);
            }
            r.popScissor();
            r.rect(actionsX + BUTTON + extra - 0.5f, y + 8f, 0.6f, HEIGHT - 16f, Colors.fade(Theme.HAIRLINE, Math.min(1f, extra / 40f)));
        }

        // Configs, with a drop behind it while the configs are open.
        float cx = actionsX + actionsW - BUTTON / 2f;
        boolean overConfigs = ctx.hovered(actionsX + BUTTON + extra, y, BUTTON, HEIGHT);
        if (overConfigs) {
            ctx.cursor(CursorTypes.POINTING_HAND);
            ctx.tooltip("Configs: save and load your setups.");
        }
        configsHover.animateTo(overConfigs ? 1 : 0);
        configsOn.animateTo(host.configsOpen() ? 1 : 0);
        float on = configsOn.get();
        if (on > 0.001f) {
            float size = (HEIGHT - INSET * 2) * (0.8f + 0.2f * on);
            r.pushAlpha(on);
            ctx.glass(ctx.theme.droplet, cx - size / 2f, y + (HEIGHT - size) / 2f, size, size, size / 2f, materialize * on);
            r.popAlpha();
        }
        Icons.folder(r, cx, y + HEIGHT / 2f, 11f, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(configsHover.get(), on)));
    }

    private void drawCaret(Render2D r, float x, float textY) {
        if (!focused) return;
        boolean on = System.currentTimeMillis() - lastEdit < 500 || (System.currentTimeMillis() / 530) % 2 == 0;
        if (on) r.rect(x, textY - 1f, 0.8f, 9f, Theme.TEXT);
    }

    // ------------------------------------------------------------ input

    /** Whether the point is on the search field or its button, where a click keeps search focused. */
    boolean overSearch(double mouseX, double mouseY) {
        return mouseY >= y && mouseY < y + HEIGHT && mouseX >= actionsX && mouseX < actionsX + actionsW - BUTTON;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
        if (mouseY < y || mouseY >= y + HEIGHT || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (int i = 0; i < tabCategories.size() && i < tabStarts.length; i++) {
            if (mouseX >= tabStarts[i] && mouseX < tabStarts[i] + tabWidths[i]) {
                if (searching()) closeSearch(host);
                host.selectCategory(tabCategories.get(i));
                return true;
            }
        }
        if (mouseX >= actionsX && mouseX < actionsX + actionsW) {
            if (mouseX >= actionsX + actionsW - BUTTON) {
                host.toggleConfigs();
            } else {
                searchOpen = true;
                focused = true;
                lastEdit = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }
}
