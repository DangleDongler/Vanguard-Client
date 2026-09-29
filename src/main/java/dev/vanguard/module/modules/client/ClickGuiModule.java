package dev.vanguard.module.modules.client;

import dev.vanguard.Vanguard;
import dev.vanguard.gui.clickgui.ClickGuiScreen;
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

    /** The accent from before the monochrome redesign. Configs still holding it get the new default. */
    private static final int OLD_DEFAULT_ACCENT = 0xFF7B61FF;

    {
        section("Appearance");
    }

    public final ColorSetting accent = add(new ColorSetting("Accent", "Color of switches, sliders and highlights.", 0xFFCACACA, false));
    public final BoolSetting rainbow = bool("Rainbow", "Cycle the accent through every hue.", false);
    public final NumberSetting rainbowSpeed = number("Rainbow Speed", "Seconds per full hue cycle.", 6, 1, 20, 0.5, "s")
        .visibleWhen(rainbow::isOn);
    public final EnumSetting<Font> font = mode("Font", "Typeface used by the GUI.", Font.INTER);
    public final NumberSetting scale = number("Scale", "Size of the GUI, independent of Minecraft's GUI scale.", 1, 0.5, 2, 0.25, "x");

    {
        section("Background");
    }

    public final BoolSetting blur = bool("Blur", "Blur the world behind the GUI. Strength follows Minecraft's menu blur option.", true);
    public final NumberSetting dim = number("Dim", "Darken the world behind the GUI.", 35, 0, 90, 5, "%");

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

    /** Moves configs saved before the redesign onto the new monochrome accent. */
    public void migrateOldDefaults() {
        if (accent.argb() == OLD_DEFAULT_ACCENT) accent.reset();
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
