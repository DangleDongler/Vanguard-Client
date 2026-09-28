package dev.vanguard.gui.clickgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The ClickGUI: a sidebar of category tabs on the left, and the open tabs' panels laid
 * out in a grid beside it. Everything is measured in its own units (2 physical pixels
 * at scale 1), so it looks the same whatever Minecraft's GUI scale is.
 */
public final class ClickGuiScreen extends Screen implements Sidebar.Host {
    private static final float MARGIN = 8f;
    private static final float GAP = 8f;
    private static final float MIN_PANEL_WIDTH = 142f;
    private static final float MAX_PANEL_WIDTH = 168f;
    private static final float TOOLTIP_MAX_WIDTH = 150f;
    private static final long TOOLTIP_DELAY_MS = 400;
    private static final long STAGGER_MS = 35;

    private final ConfigManager config;
    private final ClickGuiModule settings;
    private final Render2D render = new Render2D();
    private final Theme theme = new Theme();
    private final GuiContext ctx = new GuiContext(render, theme);
    private final Sidebar sidebar;
    /** In category order, which is also layout order. */
    private final List<Panel> panels = new ArrayList<>();
    private final Set<Category> openTabs = EnumSet.complementOf(EnumSet.of(Category.CLIENT));

    private Animation openProgress = new Animation(0, 300, Easing.QUINT_OUT);
    private final Animation tooltipFade = new Animation(0, 150, Easing.CUBIC_OUT);
    private final Animation emptyFade = new Animation(0, 200, Easing.CUBIC_OUT);
    private final Animation scroll = new Animation(0, 220, Easing.CUBIC_OUT);
    private float scrollTarget;
    private float maxScroll;
    private boolean closing;
    /** Set when a widget consumed a key press, so the character event GLFW sends for it is dropped. */
    private boolean swallowChar;
    private Panel pressedPanel;
    private String lastQuery = "";

    private String tooltip;
    private long tooltipSince;

    private float unitScale = 1f;
    private float guiWidth;
    private float guiHeight;

    public ClickGuiScreen(ModuleManager modules, ConfigManager config) {
        super(Component.literal("Vanguard"));
        this.config = config;
        this.settings = modules.get(ClickGuiModule.class);
        String version = FabricLoader.getInstance().getModContainer("vanguard")
            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
            .orElse("dev");

        List<Category> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            if (modules.inCategory(category).isEmpty()) continue;
            categories.add(category);
            String title = category == Category.CLIENT ? "Settings" : category.displayName();
            panels.add(new Panel(category, title, modules.inCategory(category)));
        }
        this.sidebar = new Sidebar(categories, version);
        restoreState();
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void added() {
        closing = false;
        openProgress = new Animation(0, 300, Easing.QUINT_OUT);
        openProgress.animateTo(1);
        sidebar.search().clear();
        lastQuery = "";
        pressedPanel = null;
        settings.setEnabled(true);

        // Panels cascade in one after another.
        int index = 0;
        for (Panel panel : panels) {
            panel.hideInstantly();
            if (openTabs.contains(panel.category())) panel.show(true, 70 + STAGGER_MS * index++);
        }
    }

    @Override
    protected void init() {
        updateScale();
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        openProgress = new Animation(openProgress.get(), 160, Easing.CUBIC_OUT);
        openProgress.animateTo(0);
    }

    /** Closes with the fade-out animation. */
    public void close() {
        onClose();
    }

    @Override
    public void removed() {
        settings.setEnabled(false);
        closing = false;
        pressedPanel = null;
        for (Panel panel : panels) panel.clickedOutside();
        saveState();
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void restoreState() {
        JsonObject gui = config.guiState();
        if (gui.get("openTabs") instanceof JsonArray saved) {
            openTabs.clear();
            for (JsonElement name : saved) {
                for (Category category : Category.values()) {
                    if (name.isJsonPrimitive() && category.name().equals(name.getAsString())) openTabs.add(category);
                }
            }
        }
        if (gui.get("expandedModules") instanceof JsonArray expanded) {
            for (Panel panel : panels) panel.loadExpanded(expanded);
        }
    }

    private void saveState() {
        JsonObject gui = config.guiState();
        JsonArray tabs = new JsonArray();
        for (Category category : openTabs) tabs.add(category.name());
        JsonArray expanded = new JsonArray();
        for (Panel panel : panels) panel.saveExpanded(expanded);
        gui.add("openTabs", tabs);
        gui.add("expandedModules", expanded);
        gui.remove("panels"); // free-floating layout from before the sidebar
    }

    // ------------------------------------------------------------ tabs

    private String query() {
        return sidebar.search().query();
    }

    private Panel panel(Category category) {
        for (Panel panel : panels) {
            if (panel.category() == category) return panel;
        }
        throw new IllegalArgumentException(category.name());
    }

    /** Shows the open tabs, or while searching, every panel with a match. */
    private void updateVisibility() {
        String query = query();
        for (Panel panel : panels) {
            boolean show = query.isEmpty() ? openTabs.contains(panel.category()) : panel.matches(query) > 0;
            if (show != panel.isShown()) panel.show(show, 0);
        }
    }

    @Override
    public boolean isOpen(Category category) {
        return panel(category).isShown();
    }

    @Override
    public void tabClicked(Category category) {
        if (searching()) {
            sidebar.search().clear();
            openTabs.add(category);
        } else if (!openTabs.remove(category)) {
            openTabs.add(category);
        }
        updateVisibility();
    }

    @Override
    public int badge(Category category) {
        if (searching()) return panel(category).matches(query());
        // The ClickGUI module is always on while this screen is open, so its count says nothing.
        return category == Category.CLIENT ? 0 : panel(category).enabledCount();
    }

    @Override
    public boolean searching() {
        return !query().isEmpty();
    }

    private void closeTab(Category category) {
        openTabs.remove(category);
        panel(category).show(false, 0);
    }

    // ------------------------------------------------------------ rendering

    private void updateScale() {
        Window window = minecraft.getWindow();
        unitScale = 2f * settings.scale.floatValue() / window.getGuiScale();
        guiWidth = width / unitScale;
        guiHeight = height / unitScale;
    }

    private float contentX() {
        return MARGIN + Sidebar.WIDTH + GAP;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Menu blur strength is Minecraft's own accessibility option; 0 means the player turned it off.
        if (settings.blur.isOn() && minecraft.options.getMenuBackgroundBlurriness() >= 1) {
            graphics.blurBeforeThisStratum();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Character events arrive in the same input poll as their key press, so one frame later
        // any character belonging to a swallowed key has been delivered.
        swallowChar = false;
        Animation.setGlobalSpeed(settings.animationSpeed.floatValue());
        theme.update(settings);
        updateScale();
        render.begin(graphics, settings.font.is(ClickGuiModule.Font.INTER), 2f * settings.scale.floatValue());

        float open = openProgress.get();
        if (closing && openProgress.isDone()) {
            // Leave the screen after this frame rather than in the middle of drawing it.
            minecraft.execute(() -> {
                if (minecraft.screen == this) minecraft.setScreen(null);
            });
        }

        int dim = Math.round(settings.dim.floatValue() / 100f * 255f * open);
        graphics.fillGradient(0, 0, width, height, Colors.withAlpha(0x06060B, Math.round(dim * 0.8f)), Colors.withAlpha(0x06060B, dim));

        Window window = minecraft.getWindow();
        float mx = (float) (minecraft.mouseHandler.getScaledXPos(window) / unitScale);
        float my = (float) (minecraft.mouseHandler.getScaledYPos(window) / unitScale);

        String query = query();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scrollTarget = 0;
            updateVisibility();
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(unitScale);
        render.pushAlpha(open);
        ctx.beginFrame(mx, my);

        layoutPanels();
        boolean overSidebar = mx < MARGIN + Sidebar.WIDTH + GAP / 2f;
        for (Panel panel : panels) {
            boolean owns = pressedPanel != null ? panel == pressedPanel : !overSidebar && panel.contains(mx, my);
            ctx.mouseX = owns ? mx : -1e6f;
            ctx.mouseY = owns ? my : -1e6f;
            panel.render(ctx, query, !query.isEmpty());
        }
        ctx.mouseX = mx;
        ctx.mouseY = my;
        drawEmptyState(query);
        drawScrollbar();

        // The sidebar slides in from the left as the menu opens.
        pose.pushMatrix();
        pose.translate(-14f * (1f - open), 0);
        if (pressedPanel != null) ctx.mouseX = -1e6f;
        sidebar.render(ctx, MARGIN, MARGIN, guiHeight - MARGIN * 2, this);
        ctx.mouseX = mx;
        pose.popMatrix();

        drawTooltip(ctx.tooltip(), mx, my);

        render.popAlpha();
        pose.popMatrix();

        if (ctx.cursor() != null) graphics.requestCursor(ctx.cursor());
    }

    /**
     * Places panels in a grid beside the sidebar: as many columns as fit, filled left to right,
     * each column stacking its panels. Opening and closing panels grow and shrink in place, so
     * the rest of the grid glides instead of jumping.
     */
    private void layoutPanels() {
        float contentX = contentX();
        float contentWidth = guiWidth - contentX - MARGIN;
        int columns = Math.max(1, (int) ((contentWidth + GAP) / (MIN_PANEL_WIDTH + GAP)));
        float panelWidth = Math.clamp((contentWidth - (columns - 1) * GAP) / columns, Math.min(MIN_PANEL_WIDTH, contentWidth), MAX_PANEL_WIDTH);

        float[] bottoms = new float[columns];
        int index = 0;
        for (Panel panel : panels) {
            if (!panel.isPresent()) continue;
            int column = index++ % columns;
            panel.place(contentX + column * (panelWidth + GAP), MARGIN + bottoms[column] - scroll.get(), panelWidth, column);
            bottoms[column] += panel.layoutHeight() + GAP * panel.presence();
        }

        float contentHeight = 0;
        for (float bottom : bottoms) contentHeight = Math.max(contentHeight, bottom - GAP);
        maxScroll = Math.max(0, contentHeight - (guiHeight - MARGIN * 2));
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);
    }

    private void drawScrollbar() {
        if (maxScroll <= 0) return;
        float trackTop = MARGIN, trackHeight = guiHeight - MARGIN * 2;
        float thumbHeight = Math.max(20f, trackHeight * trackHeight / (trackHeight + maxScroll));
        float thumbY = trackTop + (trackHeight - thumbHeight) * (scroll.get() / maxScroll);
        render.roundedRect(guiWidth - 4f, thumbY, 2f, thumbHeight, 1f, theme.accent(120));
    }

    /** A hint in the middle of the content area when there's nothing to show. */
    private void drawEmptyState(String query) {
        boolean anyShown = false;
        for (Panel panel : panels) anyShown |= panel.isShown();
        emptyFade.animateTo(anyShown ? 0 : 1);
        float fade = emptyFade.get();
        if (fade <= 0.001f) return;

        float centerX = contentX() + (guiWidth - contentX() - MARGIN) / 2f;
        float centerY = guiHeight / 2f;
        String title = query.isEmpty() ? "No tabs open" : "No modules match \u201C" + query + "\u201D";
        String hint = query.isEmpty() ? "Pick a category on the left to open it." : "Try a shorter search, or press Esc to clear it.";

        float cardW = Math.max(render.textWidth(title, true), render.smallWidth(hint)) + 36f, cardH = 70f;
        float cardX = centerX - cardW / 2f, cardY = centerY - cardH / 2f;
        render.pushAlpha(fade);
        render.shadow(cardX, cardY, cardW, cardH, 9f, 14f, Theme.SHADOW);
        render.roundedRect(cardX, cardY, cardW, cardH, 9f, Theme.PANEL);
        render.roundedOutline(cardX, cardY, cardW, cardH, 9f, 0.6f, Theme.OUTLINE);
        float iconY = cardY + 20f;
        render.circle(centerX, iconY, 11f, theme.accent(30));
        if (query.isEmpty()) Icons.grid(render, centerX, iconY, 9f, theme.accent());
        else Icons.search(render, centerX, iconY, 9f, theme.accent());
        render.textCentered(title, centerX, cardY + 37f, Theme.TEXT, true);
        render.small(hint, centerX - render.smallWidth(hint) / 2f, cardY + 51f, Theme.TEXT_DIM);
        render.popAlpha();
    }

    private void drawTooltip(String text, float mx, float my) {
        if (!settings.descriptions.isOn() || pressedPanel != null) text = null;
        long now = System.currentTimeMillis();
        if (text == null || !text.equals(tooltip)) {
            tooltip = text;
            tooltipSince = now;
            tooltipFade.snap(0);
        }
        if (tooltip == null) return;
        tooltipFade.animateTo(now - tooltipSince >= TOOLTIP_DELAY_MS ? 1 : 0);
        float fade = tooltipFade.get();
        if (fade <= 0.001f) return;

        List<String> lines = wrap(tooltip, TOOLTIP_MAX_WIDTH);
        float textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, render.textWidth(line));
        float lineHeight = render.lineHeight() + 2f;
        float w = textWidth + 12f, h = lines.size() * lineHeight + 7f;
        float x = Math.min(mx + 10f, guiWidth - w - 4f);
        float y = my + 12f + h > guiHeight - 4f ? my - h - 6f : my + 12f;

        render.pushAlpha(fade);
        render.shadow(x, y, w, h, 5f, 10f, 0xA0000000);
        render.roundedRect(x, y, w, h, 5f, 0xF6121118);
        render.roundedOutline(x, y, w, h, 5f, 0.6f, theme.accent(70));
        float ly = y + 4f;
        for (String line : lines) {
            render.text(line, x + 6f, ly, Theme.TEXT_DIM);
            ly += lineHeight;
        }
        render.popAlpha();
    }

    private List<String> wrap(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && render.textWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    // ------------------------------------------------------------ input

    private float toGui(double coordinate) {
        return (float) (coordinate / unitScale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        float mx = toGui(event.x()), my = toGui(event.y());
        for (Panel panel : panels) panel.clickedOutside();
        if (sidebar.mouseClicked(mx, my, event.button(), this)) return true;
        for (Panel panel : panels) {
            if (panel.contains(mx, my)) {
                pressedPanel = panel;
                panel.mouseClicked(mx, my, event.button(), () -> closeTab(panel.category()));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        float mx = toGui(event.x()), my = toGui(event.y());
        for (Panel panel : panels) panel.mouseReleased(mx, my, event.button());
        pressedPanel = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        // Drags are followed every frame from the live mouse position.
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (toGui(x) >= contentX() - GAP / 2f) scrollTarget -= (float) scrollY * 30f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        int key = event.key();
        for (Panel panel : panels) {
            if (panel.isCapturingKeyboard()) {
                panel.keyPressed(key, event.modifiers());
                swallowChar = true;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searching()) sidebar.search().clear();
            else onClose();
            return true;
        }
        if (sidebar.search().keyPressed(key, event.hasControlDownWithQuirk())) return true;
        if (!searching() && settings.bind().matches(key)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing || swallowChar) return true;
        for (Panel panel : panels) {
            if (panel.isCapturingKeyboard()) return true;
        }
        return sidebar.search().charTyped(event.codepoint());
    }
}
