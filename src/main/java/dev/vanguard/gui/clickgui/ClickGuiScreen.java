package dev.vanguard.gui.clickgui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.render.Backdrop;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ClickGUI: liquid glass floating over the live world. A bar of capsules across the top
 * (brand, categories, search and configs), and below it two panes: the modules, and the settings
 * of the one that's open. Everything is measured in the menu's own units (2 physical pixels at
 * scale 1), so it looks the same whatever Minecraft's GUI scale is.
 */
public final class ClickGuiScreen extends Screen implements TopBar.Host, ModuleList.Host {
    private static final float EDGE = 14f;
    private static final float MAX_WIDTH = 580f;
    private static final float MAX_HEIGHT = 440f;
    private static final float GAP = 12f;
    private static final float TOOLTIP_MAX_WIDTH = 170f;
    private static final long TOOLTIP_DELAY_MS = 450;

    private final ConfigManager config;
    private final ClickGuiModule settings;
    private final Render2D render = new Render2D();
    private final Theme theme = new Theme();
    private final GuiContext ctx = new GuiContext(render, theme);
    private final TopBar topBar = new TopBar();
    private final ModuleList moduleList = new ModuleList();
    private final Inspector inspector = new Inspector();
    private final List<Category> categories = new ArrayList<>();
    private final Map<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);
    private final Map<Category, Module> lastOpened = new EnumMap<>(Category.class);
    private final List<Module> modules = new ArrayList<>();
    private final Map<Module, ModulePage> modulePages = new IdentityHashMap<>();
    private final ConfigsPage configsPage;
    private Category category;
    private Page page;
    private List<Module> searchResults = List.of();

    // Each piece materializes in turn when the menu opens.
    private Animation barIn = new Animation(0, 1, Easing.LINEAR);
    private Animation listIn = new Animation(0, 1, Easing.LINEAR);
    private Animation paneIn = new Animation(0, 1, Easing.LINEAR);
    private Animation pageProgress = new Animation(1, 300, Easing.QUINT_OUT);
    private final Animation tooltipFade = new Animation(0, 150, Easing.CUBIC_OUT);
    private boolean closing;
    /** Set when a widget consumed a key press, so the character event GLFW sends for it is dropped. */
    private boolean swallowChar;
    private boolean dragging;
    private TextureSetup backdrop;

    private String tooltip;
    private long tooltipSince;

    private float unitScale = 1f;
    private float guiWidth;
    private float guiHeight;

    public ClickGuiScreen(ModuleManager moduleManager, ConfigManager config) {
        super(Component.literal("Vanguard"));
        this.config = config;
        this.settings = moduleManager.get(ClickGuiModule.class);
        this.configsPage = new ConfigsPage(config);

        for (Category category : Category.values()) {
            List<Module> inCategory = moduleManager.inCategory(category);
            if (inCategory.isEmpty()) continue;
            categories.add(category);
            modulesByCategory.put(category, inCategory);
            modules.addAll(inCategory);
        }
        restoreState();
    }

    // ------------------------------------------------------------ navigation

    private ModulePage pageFor(Module module) {
        return modulePages.computeIfAbsent(module, ModulePage::new);
    }

    private void open(Page next) {
        if (next == page) return;
        if (page != null) forEachWidget(Widget::clickedOutside);
        page = next;
        page.onShow();
        inspector.resetScroll();
        pageProgress = new Animation(0, 300, Easing.QUINT_OUT);
        pageProgress.animateTo(1);
        if (page.module() != null) lastOpened.put(page.module().category(), page.module());
    }

    @Override
    public List<Category> categories() {
        return categories;
    }

    @Override
    public Category category() {
        return category;
    }

    @Override
    public void selectCategory(Category next) {
        if (next == category && page != configsPage) return;
        category = next;
        moduleList.resetScroll();
        // Show one of the category's modules, the one opened last if there was one.
        List<Module> inCategory = modulesByCategory.getOrDefault(next, List.of());
        Module module = lastOpened.get(next);
        if (module == null && !inCategory.isEmpty()) module = inCategory.getFirst();
        if (module != null) open(pageFor(module));
    }

    @Override
    public boolean configsOpen() {
        return page == configsPage;
    }

    @Override
    public void toggleConfigs() {
        if (page == configsPage) selectCategory(category);
        else open(configsPage);
    }

    @Override
    public void searchChanged() {
        String query = topBar.query().toLowerCase(Locale.ROOT);
        moduleList.resetScroll();
        if (query.isEmpty()) {
            searchResults = List.of();
            return;
        }
        List<Module> byName = new ArrayList<>(), byDescription = new ArrayList<>();
        for (Module module : modules) {
            if (module.name().toLowerCase(Locale.ROOT).contains(query)) byName.add(module);
            else if (module.description().toLowerCase(Locale.ROOT).contains(query)) byDescription.add(module);
        }
        byName.addAll(byDescription);
        searchResults = byName;
    }

    @Override
    public boolean isSelected(Module module) {
        return page instanceof ModulePage modulePage && modulePage.module() == module;
    }

    @Override
    public void select(Module module) {
        open(pageFor(module));
    }

    private void forEachWidget(java.util.function.Consumer<Widget> action) {
        if (page == null) return;
        for (Section section : page.sections()) {
            for (Widget widget : section.widgets()) action.accept(widget);
        }
    }

    private Widget capturingWidget() {
        if (page == null) return null;
        for (Section section : page.sections()) {
            for (Widget widget : section.widgets()) {
                if (widget.isCapturingKeyboard()) return widget;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void added() {
        closing = false;
        dragging = false;
        barIn = new Animation(0, 520, Easing.QUINT_OUT);
        listIn = new Animation(0, 560, Easing.QUINT_OUT);
        paneIn = new Animation(0, 600, Easing.QUINT_OUT);
        barIn.animateTo(1);
        listIn.animateTo(1, 50);
        paneIn.animateTo(1, 100);
        settings.setEnabled(true);
        if (page != null) page.onShow();
    }

    @Override
    protected void init() {
        updateScale();
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        barIn = new Animation(barIn.get(), 200, Easing.CUBIC_OUT);
        listIn = new Animation(listIn.get(), 180, Easing.CUBIC_OUT);
        paneIn = new Animation(paneIn.get(), 160, Easing.CUBIC_OUT);
        barIn.animateTo(0);
        listIn.animateTo(0);
        paneIn.animateTo(0);
    }

    /** Closes with the fade-out animation. */
    public void close() {
        onClose();
    }

    @Override
    public void removed() {
        settings.setEnabled(false);
        closing = false;
        dragging = false;
        forEachWidget(Widget::clickedOutside);
        topBar.closeSearch(this);
        Backdrop.get().release();
        saveState();
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void restoreState() {
        JsonObject gui = config.guiState();
        String savedCategory = string(gui, "category");
        for (Category candidate : categories) {
            if (candidate.name().equals(savedCategory)) category = candidate;
        }
        if (category == null && !categories.isEmpty()) category = categories.getFirst();

        String saved = string(gui, "page");
        if (saved.equals("configs")) {
            page = configsPage;
            return;
        }
        for (Module module : modules) {
            if (saved.equals("module:" + module.name())) page = pageFor(module);
        }
        if (page == null && category != null) page = pageFor(modulesByCategory.get(category).getFirst());
        if (page != null && page.module() != null) {
            category = page.module().category();
            lastOpened.put(category, page.module());
        }
    }

    private static String string(JsonObject object, String key) {
        return object.get(key) instanceof JsonElement element && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private void saveState() {
        JsonObject gui = config.guiState();
        if (category != null) gui.addProperty("category", category.name());
        if (page == configsPage) gui.addProperty("page", "configs");
        else if (page instanceof ModulePage modulePage) gui.addProperty("page", "module:" + modulePage.module().name());
        // Layout from earlier menus.
        gui.remove("expandedCategories");
        gui.remove("openTabs");
        gui.remove("expandedModules");
        gui.remove("panels");
    }

    // ------------------------------------------------------------ rendering

    private void updateScale() {
        Window window = minecraft.getWindow();
        unitScale = 2f * settings.scale.floatValue() / window.getGuiScale();
        guiWidth = width / unitScale;
        guiHeight = height / unitScale;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Stops the GUI renderer here, where the glass captures the frame it looks through.
        graphics.blurBeforeThisStratum();
        backdrop = Backdrop.get().request();
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
        render.setBackdrop(backdrop);

        float bar = barIn.get(), list = listIn.get(), pane = paneIn.get();
        if (closing && barIn.isDone() && listIn.isDone() && paneIn.isDone()) {
            // Leave the screen after this frame rather than in the middle of drawing it.
            minecraft.execute(() -> {
                if (minecraft.screen == this) minecraft.setScreen(null);
            });
        }

        // The world behind, softened and darkened as the menu comes in.
        render.pushAlpha(bar);
        render.backdrop(settings.blur.isOn() ? 0.75f : 0f, settings.dim.floatValue() / 100f, 0.5f);
        render.popAlpha();

        Window window = minecraft.getWindow();
        float mx = (float) (minecraft.mouseHandler.getScaledXPos(window) / unitScale);
        float my = (float) (minecraft.mouseHandler.getScaledYPos(window) / unitScale);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(unitScale);
        ctx.beginFrame(mx, my);
        // While dragging a slider, nothing else reacts to the mouse.
        if (dragging) ctx.mouseX = -1e6f;

        float compW = Math.min(MAX_WIDTH, guiWidth - EDGE * 2);
        float compX = (guiWidth - compW) / 2f;
        float top = Math.clamp((guiHeight - MAX_HEIGHT) / 2f, EDGE, 56f);
        float panesY = top + TopBar.HEIGHT + GAP;
        float panesMax = guiHeight - panesY - EDGE;

        drawPiece(bar, compX + compW / 2f, top, -8f, () -> topBar.render(ctx, compX, top, compW, this, bar));

        String listTitle, listCaption;
        List<Module> listed;
        if (topBar.searching()) {
            listTitle = "Search";
            listed = searchResults;
            listCaption = listed.size() == 1 ? "1 result" : listed.size() + " results";
        } else {
            listTitle = category.displayName();
            listed = modulesByCategory.getOrDefault(category, List.of());
            listCaption = enabledCount(listed) + " of " + listed.size() + " on";
        }
        drawPiece(list, compX + ModuleList.WIDTH / 2f, panesY, 10f,
            () -> moduleList.render(ctx, compX, panesY, panesMax, listTitle, listCaption, listed, this, list));

        if (page != null) {
            float paneX = compX + ModuleList.WIDTH + GAP, paneW = compW - ModuleList.WIDTH - GAP;
            if (dragging) ctx.mouseX = mx;
            drawPiece(pane, paneX + paneW / 2f, panesY, 10f,
                () -> inspector.render(ctx, paneX, panesY, paneW, panesMax, page, pageProgress.get(), pane));
        }

        ctx.mouseX = mx;
        drawTooltip(ctx.tooltip(), mx, my);
        pose.popMatrix();

        if (ctx.cursor() != null) graphics.requestCursor(ctx.cursor());
    }

    /**
     * Draws one piece of the menu while it materializes: fading in, rising from {@code rise} units
     * away and growing slightly around its top center.
     */
    private void drawPiece(float progress, float anchorX, float anchorY, float rise, Runnable draw) {
        if (progress <= 0.002f) return;
        Matrix3x2fStack pose = render.pose();
        pose.pushMatrix();
        float scale = 0.95f + 0.05f * progress;
        pose.translate(anchorX, anchorY + rise * (1f - progress));
        pose.scale(scale);
        pose.translate(-anchorX, -anchorY);
        render.pushAlpha(progress);
        draw.run();
        render.popAlpha();
        pose.popMatrix();
    }

    private static int enabledCount(List<Module> modules) {
        int count = 0;
        for (Module module : modules) {
            if (module.persistsEnabledState() && module.isEnabled()) count++;
        }
        return count;
    }

    private void drawTooltip(String text, float mx, float my) {
        if (!settings.descriptions.isOn() || dragging) text = null;
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

        List<String> lines = render.wrapSmall(tooltip, TOOLTIP_MAX_WIDTH);
        float textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, render.smallWidth(line));
        float lineHeight = 9.5f;
        float w = textWidth + 16f, h = lines.size() * lineHeight + 9f;
        float x = Math.min(mx + 10f, guiWidth - w - 4f);
        float y = my + 14f + h > guiHeight - 4f ? my - h - 6f : my + 14f;

        render.pushAlpha(fade);
        render.shadow(x, y, w, h, 8f, 12f, 0x90000000);
        render.roundedRect(x, y, w, h, 8f, Theme.POPOVER);
        render.roundedOutline(x, y, w, h, 8f, 0.6f, Theme.POPOVER_EDGE);
        float ly = y + 5f;
        for (String line : lines) {
            render.small(line, x + 8f, ly, Theme.TEXT_DIM);
            ly += lineHeight;
        }
        render.popAlpha();
    }

    // ------------------------------------------------------------ input

    private float toGui(double coordinate) {
        return (float) (coordinate / unitScale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        float mx = toGui(event.x()), my = toGui(event.y());
        forEachWidget(Widget::clickedOutside);
        if (!topBar.overSearch(mx, my)) topBar.clickedOutside();
        ctx.pressed(mx, my);
        if (topBar.mouseClicked(mx, my, event.button(), this)) return true;
        if (moduleList.mouseClicked(mx, my, event.button(), this)) return true;
        if (page != null && inspector.mouseClicked(mx, my, event.button(), page)) dragging = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        float mx = toGui(event.x()), my = toGui(event.y());
        forEachWidget(widget -> widget.mouseReleased(mx, my, event.button()));
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        // Drags are followed every frame from the live mouse position.
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        float mx = toGui(x), my = toGui(y);
        if (moduleList.contains(mx, my)) moduleList.mouseScrolled(scrollY);
        else if (inspector.contains(mx, my)) inspector.mouseScrolled(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        int key = event.key();
        Widget capturing = capturingWidget();
        if (capturing != null) {
            // A key the widget used (Enter, a keybind) mustn't also arrive as typed text.
            if (capturing.keyPressed(key, event.modifiers())) swallowChar = true;
            return true;
        }
        if (topBar.isCapturingKeyboard()) {
            if (topBar.keyPressed(key, event.modifiers(), this)) swallowChar = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && topBar.searching()) {
            topBar.closeSearch(this);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || settings.bind().matches(key)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing || swallowChar) return true;
        Widget capturing = capturingWidget();
        if (capturing != null) return capturing.charTyped(event.codepoint());
        if (topBar.isCapturingKeyboard()) return topBar.charTyped(event.codepoint(), this);
        // Typing anywhere else starts a search.
        if (!Character.isWhitespace(event.codepoint()) && !Character.isISOControl(event.codepoint())) {
            topBar.typeToSearch(event.codepoint(), this);
            return true;
        }
        return false;
    }
}
