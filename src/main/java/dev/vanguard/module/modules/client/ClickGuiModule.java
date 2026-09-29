package dev.vanguard.module.modules.client;

import dev.vanguard.Vanguard;
import dev.vanguard.gui.clickgui.ClickGuiScreen;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.util.Keys;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Opens the ClickGUI and holds its appearance settings. Enabled exactly while the GUI is open. */
public final class ClickGuiModule extends Module {
    public enum Font { INTER, MINECRAFT }

    /** Accents that were the default in earlier designs. Configs still holding one get the new default. */
    private static final int MONOCHROME_ACCENT = 0xFFCACACA;
    private static final double MONOCHROME_DIM = 35;
    private static final int[] OLD_DEFAULT_ACCENTS = {0xFF7B61FF, MONOCHROME_ACCENT};

    {
        section("Appearance");
    }

    public final ColorSetting accent = add(new ColorSetting("Accent", "Color of switches, sliders and highlights.", Theme.DEFAULT_ACCENT, false));
    public final BoolSetting rainbow = bool("Rainbow", "Cycle the accent through every hue.", false);
    public final NumberSetting rainbowSpeed = number("Rainbow Speed", "Seconds per full hue cycle.", 6, 1, 20, 0.5, "s")
        .visibleWhen(rainbow::isOn);
    public final EnumSetting<Font> font = mode("Font", "Typeface used by the GUI.", Font.INTER);
    public final NumberSetting scale = number("Scale", "Size of the GUI, independent of Minecraft's GUI scale.", 1, 0.5, 2, 0.25, "x");

    {
        section("Glass");
    }

    public final NumberSetting refraction = number("Refraction", "How strongly the glass edges bend the world behind them.", 100, 0, 200, 10, "%");
    public final NumberSetting frost = number("Frost", "How blurred the world looks through the glass.", 100, 0, 100, 5, "%");
    public final NumberSetting tint = number("Tint", "How dark the glass is. Darker glass keeps text readable over bright scenes.", 55, 0, 100, 5, "%");

    {
        section("Background");
    }

    public final BoolSetting blur = bool("Blur", "Blur the world behind the menu.", false);
    public final NumberSetting dim = number("Dim", "Darken the world behind the menu.", 20, 0, 90, 5, "%");

    {
        section("Behavior");
    }

    public final NumberSetting animationSpeed = number("Animation Speed", "Multiplier for every GUI animation.", 1, 0.25, 3, 0.25, "x");
    public final BoolSetting descriptions = bool("Descriptions", "Show a description when hovering settings.", true);

    public ClickGuiModule() {
        super("ClickGUI", "Opens this menu. Change how it looks and behaves.", Category.CLIENT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        // Unbinding the menu would lock the player out of it.
        bind().onChange(key -> {
            if (key == Keys.NONE) bind().reset();
        });
    }

    /**
     * Moves configs saved with an earlier design's default accent onto the current one. A config
     * still on the monochrome design's defaults also gets the glass design's background (sharp and
     * lightly dimmed, so the glass has something to bend); the accent changing makes this run once.
     */
    public void migrateOldDefaults() {
        for (int old : OLD_DEFAULT_ACCENTS) {
            if (accent.argb() != old) continue;
            if (old == MONOCHROME_ACCENT && blur.isOn() && dim.get() == MONOCHROME_DIM) {
                blur.reset();
                dim.reset();
            }
            accent.reset();
        }
    }

    @Override
    public boolean persistsEnabledState() {
        return false;
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ClickGuiScreen)) {
            mc.setScreen(Vanguard.get().clickGui());
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClickGuiScreen screen) screen.close();
    }
}
