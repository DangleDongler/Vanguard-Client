package dev.vanguard.gui.clickgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ClickGUI: the sidebar on the left, and the open page on the right: a header bar with the
 * page's name and description, then its sections in a column. Everything is measured in its own
 * units (2 physical pixels at scale 1), so it looks the same whatever Minecraft's GUI scale is.
 */
public final class ClickGuiScreen extends Screen implements Sidebar.Host {
    private static final float MARGIN = 10f;
    private static final float GAP = 16f;
    private static final float HEADER_HEIGHT = 25f;
    private static final float HEADER_GAP = 14f;
    private static final float COLUMN_WIDTH = 252f;
    private static final float SECTION_GAP = 12f;
    private static final float TOOLTIP_MAX_WIDTH = 170f;
    private static final long TOOLTIP_DELAY_MS = 450;

    private final ConfigManager config;
    private final ClickGuiModule settings;
    private final Render2D render = new Render2D();
    private final Theme theme = new Theme();
    private final GuiContext ctx = new GuiContext(render, theme);
    private final Sidebar sidebar;
    private final List<Module> modules = new ArrayList<>();
    private final Map<Module, ModulePage> modulePages = new IdentityHashMap<>();
    private final ConfigsPage configsPage;
    private Page page;

    private Animation openProgress = new Animation(0, 300, Easing.QUINT_OUT);
    private Animation pageProgress = new Animation(1, 260, Easing.QUINT_OUT);
    private final Animation tooltipFade = new Animation(0, 150, Easing.CUBIC_OUT);
    private final Animation scroll = new Animation(0, 220, Easing.CUBIC_OUT);
    private float scrollTarget;
    private float maxScroll;
    private boolean closing;
    /** Set when a widget consumed a key press, so the character event GLFW sends for it is dropped. */
    private boolean swallowChar;
    private boolean dragging;

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

        List<Category> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            List<Module> inCategory = moduleManager.inCategory(category);
            if (inCategory.isEmpty()) continue;
            categories.add(category);
            modules.addAll(inCategory);
        }
        this.sidebar = new Sidebar(categories, moduleManager::inCategory);
        restoreState();
    }

    // ------------------------------------------------------------ pages

    private ModulePage pageFor(Module module) {
        return modulePages.computeIfAbsent(module, ModulePage::new);
    }

    private void open(Page next) {
        if (next == page) return;
        if (page != null) forEachWidget(Widget::clickedOutside);
        page = next;
        page.onShow();
        scrollTarget = 0;
        scroll.snap(0);
        pageProgress = new Animation(0, 260, Easing.QUINT_OUT);
        pageProgress.animateTo(1);
    }

    @Override
    public boolean isSelected(Module module) {
        return page instanceof ModulePage modulePage && modulePage.module() == module;
    }

    @Override
    public boolean configsSelected() {
        return page == configsPage;
    }

    @Override
    public void select(Module module) {
        open(pageFor(module));
    }

    @Override
    public void selectConfigs() {
        open(configsPage);
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
        openProgress = new Animation(0, 300, Easing.QUINT_OUT);
        openProgress.animateTo(1);
        dragging = false;
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
        dragging = false;
        forEachWidget(Widget::clickedOutside);
        saveState();
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void restoreState() {
        JsonObject gui = config.guiState();
        Set<Category> open = EnumSet.noneOf(Category.class);
        if (gui.get("expandedCategories") instanceof JsonArray saved) {
            for (JsonElement name : saved) {
                for (Category category : Category.values()) {
                    if (name.isJsonPrimitive() && category.name().equals(name.getAsString())) open.add(category);
                }
            }
        } else {
            open.add(Category.COMBAT);
        }
        sidebar.setExpanded(open);

        String saved = gui.get("page") instanceof JsonElement element && element.isJsonPrimitive() ? element.getAsString() : "";
        if (saved.equals("configs")) {
            page = configsPage;
            return;
        }
        for (Module module : modules) {
            if (saved.equals("module:" + module.name())) page = pageFor(module);
        }
        if (page == null && !modules.isEmpty()) page = pageFor(modules.getFirst());
        if (page instanceof ModulePage modulePage) sidebar.reveal(modulePage.module());
    }

    private void saveState() {
        JsonObject gui = config.guiState();
        JsonArray expanded = new JsonArray();
        for (Category category : sidebar.expanded()) expanded.add(category.name());
        gui.add("expandedCategories", expanded);
        if (page == configsPage) gui.addProperty("page", "configs");
        else if (page instanceof ModulePage modulePage) gui.addProperty("page", "module:" + modulePage.module().name());
        // Layout from the old tab-and-panel menu.
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

    private float contentX() {
        return MARGIN + Sidebar.WIDTH + GAP;
    }

    private float sectionsTop() {
        return MARGIN + HEADER_HEIGHT + HEADER_GAP;
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
        graphics.fillGradient(0, 0, width, height, Colors.withAlpha(0x050607, Math.round(dim * 0.85f)), Colors.withAlpha(0x050607, dim));

        Window window = minecraft.getWindow();
        float mx = (float) (minecraft.mouseHandler.getScaledXPos(window) / unitScale);
        float my = (float) (minecraft.mouseHandler.getScaledYPos(window) / unitScale);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(unitScale);
        render.pushAlpha(open);
        ctx.beginFrame(mx, my);

        // The sidebar slides in from the left, the page rises into place.
        pose.pushMatrix();
        pose.translate(-12f * (1f - open), 0);
        if (dragging) ctx.mouseX = -1e6f;
        sidebar.render(ctx, MARGIN, MARGIN, guiHeight - MARGIN * 2, this);
        ctx.mouseX = mx;
        pose.popMatrix();

        if (page != null) {
            pose.pushMatrix();
            pose.translate(0, 6f * (1f - open));
            drawHeader();
            drawSections(mx, my);
            pose.popMatrix();
        }

        drawTooltip(ctx.tooltip(), mx, my);
        render.popAlpha();
        pose.popMatrix();

        if (ctx.cursor() != null) graphics.requestCursor(ctx.cursor());
    }

    /** The bar across the top: the page's name in a chip, then what it does. */
    private void drawHeader() {
        float x = contentX(), w = guiWidth - x - MARGIN;
        render.roundedRect(x, MARGIN, w, HEADER_HEIGHT, 6f, Theme.HEADER);
        render.roundedOutline(x, MARGIN, w, HEADER_HEIGHT, 6f, 0.6f, Theme.OUTLINE);

        float p = pageProgress.get();
        render.pushAlpha(p);
        String title = page.title();
        float chipH = 16f, chipW = render.smallWidth(title) + 14f;
        float chipX = x + 5f, chipY = MARGIN + (HEADER_HEIGHT - chipH) / 2f;
        render.roundedRect(chipX, chipY, chipW, chipH, 4f, Theme.CHIP);
        render.small(title, chipX + 7f, render.smallY(chipY, chipH), Theme.TEXT);
        float descriptionX = chipX + chipW + 12f;
        String description = render.ellipsizeSmall(page.description(), x + w - 10f - descriptionX);
        render.small(description, descriptionX, render.smallY(MARGIN, HEADER_HEIGHT), Theme.HEADER_TEXT);
        render.popAlpha();
    }

    /** The page's sections in a column below the header, scrolling when they don't fit. */
    private void drawSections(float mx, float my) {
        float x = contentX(), top = sectionsTop(), bottom = guiHeight - MARGIN;
        float width = Math.min(COLUMN_WIDTH, guiWidth - x - MARGIN);

        List<Section> sections = page.sections();
        float contentHeight = 0;
        for (Section section : sections) contentHeight += section.height() + SECTION_GAP;
        contentHeight -= SECTION_GAP;
        maxScroll = Math.max(0, contentHeight - (bottom - top));
        scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
        scroll.animateTo(scrollTarget);

        boolean inside = mx >= x && mx < x + width && my >= top - 4f && my < bottom;
        if (!inside && !dragging) ctx.mouseX = -1e6f;

        float p = pageProgress.get();
        render.pushAlpha(p);
        // Room above and below for the cards' shadows.
        render.pushScissor(x - 12f, top - 4f, width + 24f, bottom - top + 8f);
        float cy = top - scroll.get() + 8f * (1f - p);
        for (Section section : sections) {
            section.render(ctx, x, cy, width);
            cy += section.height() + SECTION_GAP;
        }
        render.popScissor();
        render.popAlpha();
        ctx.mouseX = mx;

        if (maxScroll > 0) {
            float trackHeight = bottom - top;
            float thumbHeight = Math.max(20f, trackHeight * trackHeight / (trackHeight + maxScroll));
            float thumbY = top + (trackHeight - thumbHeight) * (scroll.get() / maxScroll);
            render.roundedRect(x + width + 5f, thumbY, 2f, thumbHeight, 1f, 0x40FFFFFF);
        }
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

        List<String> lines = wrap(tooltip, TOOLTIP_MAX_WIDTH);
        float textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, render.smallWidth(line));
        float lineHeight = 9f;
        float w = textWidth + 14f, h = lines.size() * lineHeight + 8f;
        float x = Math.min(mx + 10f, guiWidth - w - 4f);
        float y = my + 12f + h > guiHeight - 4f ? my - h - 6f : my + 12f;

        render.pushAlpha(fade);
        render.shadow(x, y, w, h, 5f, 10f, 0xA0000000);
        render.roundedRect(x, y, w, h, 5f, 0xF4181818);
        render.roundedOutline(x, y, w, h, 5f, 0.6f, Theme.OUTLINE);
        float ly = y + 4.5f;
        for (String line : lines) {
            render.small(line, x + 7f, ly, Theme.TEXT_DIM);
            ly += lineHeight;
        }
        render.popAlpha();
    }

    private List<String> wrap(String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && render.smallWidth(candidate) > maxWidth) {
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

    private boolean inContent(float mx, float my) {
        float x = contentX();
        return mx >= x && mx < x + COLUMN_WIDTH && my >= sectionsTop() - 4f && my < guiHeight - MARGIN;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        float mx = toGui(event.x()), my = toGui(event.y());
        forEachWidget(Widget::clickedOutside);
        if (sidebar.mouseClicked(mx, my, event.button(), this)) return true;
        if (page != null && inContent(mx, my)) {
            for (Section section : page.sections()) {
                if (section.mouseClicked(mx, my, event.button())) {
                    dragging = true;
                    return true;
                }
            }
        }
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
        if (sidebar.contains(mx, my)) sidebar.mouseScrolled(scrollY);
        else scrollTarget -= (float) scrollY * 30f;
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
        return capturing != null && capturing.charTyped(event.codepoint());
    }
}
