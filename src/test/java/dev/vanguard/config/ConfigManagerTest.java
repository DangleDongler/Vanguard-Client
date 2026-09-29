package dev.vanguard.config;

import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import dev.vanguard.module.modules.combat.TriggerBot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path dir;

    private static ModuleManager modules() {
        ModuleManager modules = new ModuleManager();
        modules.init();
        return modules;
    }

    @Test
    void roundTripsModulesSettingsBindsAndGuiState() {
        ModuleManager original = modules();
        TriggerBot triggerBot = original.get(TriggerBot.class);
        triggerBot.setEnabled(true);
        triggerBot.bind().set(75);
        triggerBot.spacing.set(87.0);
        triggerBot.crits.set(TriggerBot.Crits.ONLY);
        triggerBot.hitSelect.set(true);
        original.get(ClickGuiModule.class).accent.set(0xFF123456);
        ConfigManager config = new ConfigManager(dir, original);
        config.guiState().addProperty("marker", 7);
        config.save();

        ModuleManager restored = modules();
        ConfigManager reloaded = new ConfigManager(dir, restored);
        reloaded.load();
        TriggerBot copy = restored.get(TriggerBot.class);
        assertTrue(copy.isEnabled());
        assertEquals(75, copy.bind().key());
        assertEquals(87.0, copy.spacing.get());
        assertEquals(TriggerBot.Crits.ONLY, copy.crits.get());
        assertTrue(copy.hitSelect.isOn());
        assertEquals(0xFF123456, restored.get(ClickGuiModule.class).accent.argb());
        assertEquals(7, reloaded.guiState().get("marker").getAsInt());
    }

    @Test
    void neverRestoresTheGuiAsOpen() {
        ModuleManager original = modules();
        // Flip the field without running onEnable, which would need a running game.
        original.get(ClickGuiModule.class).bind().set(71);
        new ConfigManager(dir, original).save();

        ModuleManager restored = modules();
        new ConfigManager(dir, restored).load();
        assertFalse(restored.get(ClickGuiModule.class).isEnabled());
        assertEquals(71, restored.get(ClickGuiModule.class).bind().key());
    }

    @Test
    void clickGuiCannotBeUnbound() {
        ClickGuiModule gui = modules().get(ClickGuiModule.class);
        int original = gui.bind().key();
        gui.bind().set(dev.vanguard.util.Keys.NONE);
        assertEquals(original, gui.bind().key());
    }

    @Test
    void monochromeDefaultsMoveToTheGlassDesignOnce() {
        ClickGuiModule gui = modules().get(ClickGuiModule.class);
        gui.accent.set(0xFFCACACA);
        gui.blur.set(true);
        gui.dim.set(35.0);
        gui.migrateOldDefaults();
        assertEquals(Theme.DEFAULT_ACCENT, gui.accent.argb());
        assertFalse(gui.blur.isOn());
        assertEquals(20.0, gui.dim.get());

        // Chosen afterwards, the same values stay.
        gui.blur.set(true);
        gui.dim.set(35.0);
        gui.migrateOldDefaults();
        assertTrue(gui.blur.isOn());
        assertEquals(35.0, gui.dim.get());
    }

    @Test
    void customizedBackgroundSurvivesTheAccentMigration() {
        ClickGuiModule gui = modules().get(ClickGuiModule.class);
        gui.accent.set(0xFFCACACA);
        gui.blur.set(true);
        gui.dim.set(50.0);
        gui.migrateOldDefaults();
        assertEquals(Theme.DEFAULT_ACCENT, gui.accent.argb());
        assertTrue(gui.blur.isOn());
        assertEquals(50.0, gui.dim.get());
    }

    @Test
    void corruptFileKeepsDefaults() throws IOException {
        Files.writeString(dir.resolve("config.json"), "{ not json");
        ModuleManager modules = modules();
        new ConfigManager(dir, modules).load();
        assertEquals(100.0, modules.get(TriggerBot.class).spacing.get());
    }

    @Test
    void malformedModuleEntryIsSkipped() throws IOException {
        Files.writeString(dir.resolve("config.json"), """
            {"modules": {
              "TriggerBot": {"enabled": "maybe", "settings": {"Spacing": "far"}},
              "ShieldBreaker": {"settings": {"Reaction Time": 300.0}}
            }}
            """);
        ModuleManager modules = modules();
        new ConfigManager(dir, modules).load();
        assertEquals(100.0, modules.get(TriggerBot.class).spacing.get());
        assertEquals(300.0, modules.get(ShieldBreaker.class).reactionTime.get());
    }

    @Test
    void namedConfigsSaveLoadAndDelete() {
        ModuleManager modules = modules();
        ConfigManager config = new ConfigManager(dir, modules);
        TriggerBot triggerBot = modules.get(TriggerBot.class);
        triggerBot.spacing.set(80.0);
        assertTrue(config.saveConfig("Crystal PvP"));
        assertEquals(java.util.List.of("Crystal PvP"), config.configNames());

        triggerBot.spacing.set(60.0);
        assertTrue(config.loadConfig("Crystal PvP"));
        assertEquals(80.0, triggerBot.spacing.get());

        assertTrue(config.deleteConfig("Crystal PvP"));
        assertTrue(config.configNames().isEmpty());
        assertFalse(config.loadConfig("Crystal PvP"));
    }

    @Test
    void configNamesAreSafeFileNames() {
        assertEquals("evil", ConfigManager.cleanName("../evil"));
        assertEquals("a b-c_d", ConfigManager.cleanName("  a b-c_d  "));
        assertEquals("", ConfigManager.cleanName("///"));
        assertEquals(ConfigManager.MAX_NAME_LENGTH, ConfigManager.cleanName("x".repeat(40)).length());
        assertFalse(new ConfigManager(dir, modules()).saveConfig("..."));
    }

    @Test
    void savingAConfigDoesNotTouchTheLiveOne() {
        ConfigManager config = new ConfigManager(dir, modules());
        config.saveConfig("test");
        assertFalse(Files.exists(dir.resolve("config.json")));
    }
}
