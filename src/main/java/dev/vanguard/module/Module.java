package dev.vanguard.module;

import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.KeybindSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.setting.Setting;
import dev.vanguard.util.Keys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting bind;
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this(name, description, category, Keys.NONE);
    }

    protected Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        // Kept out of settings(); the GUI shows it as the last row.
        this.bind = new KeybindSetting("Bind", "Key that toggles " + name + ".", defaultKey);
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final KeybindSetting bind() {
        return bind;
    }

    /** Settings shown in the GUI, excluding the bind. */
    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    /** Whether the enabled state should be saved and restored across sessions. */
    public boolean persistsEnabledState() {
        return true;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected final <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    protected final BoolSetting bool(String name, String description, boolean defaultValue) {
        return add(new BoolSetting(name, description, defaultValue));
    }

    protected final NumberSetting number(String name, String description, double defaultValue, double min, double max, double step) {
        return add(new NumberSetting(name, description, defaultValue, min, max, step, ""));
    }

    protected final NumberSetting number(String name, String description, double defaultValue, double min, double max, double step, String suffix) {
        return add(new NumberSetting(name, description, defaultValue, min, max, step, suffix));
    }

    protected final <E extends Enum<E>> EnumSetting<E> mode(String name, String description, E defaultValue) {
        return add(new EnumSetting<>(name, description, defaultValue));
    }

    protected final ColorSetting color(String name, String description, int defaultArgb) {
        return add(new ColorSetting(name, description, defaultArgb, true));
    }
}
