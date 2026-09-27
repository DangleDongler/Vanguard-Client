package dev.vanguard.gui.clickgui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
import dev.vanguard.util.Keys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The ClickGUI. Everything inside is laid out in its own units (2 physical pixels at
 * scale 1), so it looks the same whatever Minecraft's GUI scale is.
 */
public final class ClickGuiScreen extends Screen {
    private static final float MARGIN = 10f;
    private static final float PANEL_GAP = 8f;
    private static final float PANELS_TOP = 36f;
    private static final float FOOTER_HEIGHT = 24f;
    private static final float TOOLTIP_MAX_WIDTH = 150f;
    private static final long TOOLTIP_DELAY_MS = 400;

    private final ModuleManager modules;
    private final ConfigManager config;
    private final ClickGuiModule settings;
    private final Render2D render = new Render2D();
    private final Theme theme = new Theme();
    private final GuiContext ctx = new GuiContext(render, theme);
    /** Back to front: the last panel is drawn on top and gets clicks first. */
    private final List<Panel> panels = new ArrayList<>();
    private final SearchField search = new SearchField();
    private final String version;

    private Animation openProgress = new Animation(0, 300, Easing.QUINT_OUT);
    private final Animation tooltipFade = new Animation(0, 150, Easing.CUBIC_OUT);
    private boolean closing;
    private boolean layoutRestored;
    /** Set when a widget consumed a key press, so the character event GLFW sends for it is dropped. */
    private boolean swallowChar;
    private Panel pressedPanel;

    private String tooltip;
    private long tooltipSince;

    private float unitScale = 1f;
    private float guiWidth;
    private float guiHeight;

    public ClickGuiScreen(ModuleManager modules, ConfigManager config) {
        super(Component.literal("Vanguard"));
        this.modules = modules;
        this.config = config;
        this.settings = modules.get(ClickGuiModule.class);
        this.version = FabricLoader.getInstance().getModContainer("vanguard")
            .map(c -> c.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
            .orElse("dev");
        for (Category category : Category.values()) {
            if (!modules.inCategory(category).isEmpty()) panels.add(new Panel(category, modules.inCategory(category), 0, 0));
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void added() {
        closing = false;
        openProgress = new Animation(0, 300, Easing.QUINT_OUT);
        openProgress.animateTo(1);
        search.clear();
        pressedPanel = null;
        settings.setEnabled(true);
    }

    @Override
    protected void init() {
        updateScale();
        if (!layoutRestored) {
            layoutRestored = true;
            restoreLayout();
        }
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
        saveLayout();
        config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void restoreLayout() {
        JsonObject saved = config.guiState().get("panels") instanceof JsonObject o ? o : new JsonObject();
        // Default layout: one row of panels. Panels that don't fit go under the shortest column.
        int columns = Math.max(1, (int) ((guiWidth - MARGIN * 2 + PANEL_GAP) / (Panel.WIDTH + PANEL_GAP)));
        float[] columnBottoms = new float[Math.min(columns, panels.size())];
        java.util.Arrays.fill(columnBottoms, PANELS_TOP);
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            int column = i;
            if (i >= columnBottoms.length) {
                column = 0;
                for (int c = 1; c < columnBottoms.length; c++) {
                    if (columnBottoms[c] < columnBottoms[column]) column = c;
                }
            }
            panel.moveTo(MARGIN + column * (Panel.WIDTH + PANEL_GAP), columnBottoms[column]);
            columnBottoms[column] += panel.collapsedHeight() + PANEL_GAP;
            if (saved.get(panel.category().name()) instanceof JsonObject state) panel.load(state);
        }
    }

    private void saveLayout() {
        JsonObject saved = new JsonObject();
        for (Panel panel : panels) saved.add(panel.category().name(), panel.save());
        config.guiState().add("panels", saved);
    }

    // ------------------------------------------------------------ rendering

    private void updateScale() {
        Window window = minecraft.getWindow();
        unitScale = 2f * settings.scale.floatValue() / window.getGuiScale();
        guiWidth = width / unitScale;
        guiHeight = height / unitScale;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Menu blur strength is Minecraft's own accessibility option; 0 means the player turned it off.
        if (settings.blur.isOn() && minecraft.options.getMenuBackgroundBlurriness() >= 1) {
            graphics.blurBeforeThisStratum();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
                if (minecraft.gui.screen() == this) minecraft.gui.setScreen(null);
            });
        }

        int dim = Math.round(settings.dim.floatValue() / 100f * 255f * open);
        graphics.fillGradient(0, 0, width, height, Colors.withAlpha(0x06060B, Math.round(dim * 0.8f)), Colors.withAlpha(0x06060B, dim));

        Window window = minecraft.getWindow();
        float mx = (float) (minecraft.mouseHandler.getScaledXPos(window) / unitScale);
        float my = (float) (minecraft.mouseHandler.getScaledYPos(window) / unitScale);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(unitScale);
        // Open animation: grow slightly from the center while sliding down into place.
        float grow = 0.96f + 0.04f * open;
        pose.translate(guiWidth / 2f, guiHeight / 2f);
        pose.scale(grow);
        pose.translate(-guiWidth / 2f, -guiHeight / 2f - 6f * (1f - open));
        render.pushAlpha(open);

        ctx.beginFrame(mx, my);
        drawFooter();
        float searchX = (guiWidth - SearchField.WIDTH) / 2f, searchY = MARGIN;
        boolean overSearch = ctx.hovered(searchX, searchY, SearchField.WIDTH, SearchField.HEIGHT);
        Panel hoverOwner = pressedPanel != null ? pressedPanel : overSearch ? null : topPanelAt(mx, my);
        String query = search.query();
        for (Panel panel : panels) {
            boolean owns = panel == hoverOwner;
            ctx.mouseX = owns ? mx : -1e6f;
            ctx.mouseY = owns ? my : -1e6f;
            panel.render(ctx, guiWidth, guiHeight - FOOTER_HEIGHT, query);
        }
        ctx.mouseX = mx;
        ctx.mouseY = my;

        search.render(ctx, searchX, searchY);
        drawTooltip(ctx.tooltip(), mx, my);

        render.popAlpha();
        pose.popMatrix();

        if (ctx.cursor() != null) graphics.requestCursor(ctx.cursor());
    }

    private void drawFooter() {
        // Keeps the footer readable over bright terrain.
        render.gradientV(0, guiHeight - FOOTER_HEIGHT * 1.8f, guiWidth, FOOTER_HEIGHT * 1.8f, 0x00000000, 0xC0000000);
        float y = guiHeight - MARGIN - render.lineHeight();

        // Logo mark: a "V" on an accent tile.
        float tile = 11f, tileY = y + 3.5f - tile / 2f;
        render.shadow(MARGIN, tileY, tile, tile, 3f, 6f, theme.accent(90));
        render.roundedRect(MARGIN, tileY, tile, tile, 3f, theme.accent());
        render.chevron(MARGIN + tile / 2f, tileY + tile / 2f + 0.3f, 5f, (float) (Math.PI / 2), 1.3f, 0xFFFFFFFF);

        float x = MARGIN + tile + 5f;
        x += render.text("Vanguard", x, y, Theme.TEXT, true);
        x += render.text(" " + version, x + 1f, y, Theme.TEXT_DIM);

        String hint = "Left-click toggle  ·  Right-click settings  ·  Drag headers  ·  "
            + Keys.name(settings.bind().key()) + " to close";
        float hintX = guiWidth - MARGIN - render.textWidth(hint);
        if (hintX > x + 16f) render.text(hint, hintX, y, Theme.TEXT_DIM);
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
        render.shadow(x, y, w, h, 4f, 10f, 0xA0000000);
        render.roundedRect(x, y, w, h, 4f, 0xF5121218);
        render.roundedOutline(x, y, w, h, 4f, 0.6f, theme.accent(70));
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

    private Panel topPanelAt(double x, double y) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).contains(x, y)) return panels.get(i);
        }
        return null;
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
        Panel target = topPanelAt(mx, my);
        if (target != null) {
            panels.remove(target);
            panels.add(target);
            pressedPanel = target;
            target.mouseClicked(mx, my, event.button());
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
        Panel target = topPanelAt(toGui(x), toGui(y));
        if (target != null) target.mouseScrolled(scrollY);
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
            if (!search.isEmpty()) search.clear();
            else onClose();
            return true;
        }
        if (search.keyPressed(key, event.hasControlDownWithQuirk())) return true;
        if (search.isEmpty() && settings.bind().matches(key)) {
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
        return search.charTyped(event.codepoint());
    }
}
