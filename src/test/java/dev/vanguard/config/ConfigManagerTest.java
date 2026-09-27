package dev.vanguard.config;

import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.client.ClickGuiModule;
import dev.vanguard.module.modules.combat.AutoCrystal;
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
        AutoCrystal crystal = original.get(AutoCrystal.class);
        crystal.setEnabled(true);
        crystal.bind().set(75);
        crystal.placeRange.set(5.2);
        crystal.swap.set(AutoCrystal.Swap.NORMAL);
        crystal.renderColor.set(0x40FF0000);
        ConfigManager config = new ConfigManager(dir, original);
        config.guiState().addProperty("marker", 7);
        config.save();

        ModuleManager restored = modules();
        ConfigManager reloaded = new ConfigManager(dir, restored);
        reloaded.load();
        AutoCrystal copy = restored.get(AutoCrystal.class);
        assertTrue(copy.isEnabled());
        assertEquals(75, copy.bind().key());
        assertEquals(5.2, copy.placeRange.get());
        assertEquals(AutoCrystal.Swap.NORMAL, copy.swap.get());
        assertEquals(0x40FF0000, copy.renderColor.argb());
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
    void corruptFileKeepsDefaults() throws IOException {
        Files.writeString(dir.resolve("config.json"), "{ not json");
        ModuleManager modules = modules();
        new ConfigManager(dir, modules).load();
        assertEquals(4.5, modules.get(AutoCrystal.class).placeRange.get());
    }

    @Test
    void malformedModuleEntryIsSkipped() throws IOException {
        Files.writeString(dir.resolve("config.json"), """
            {"modules": {
              "AutoCrystal": {"enabled": "maybe", "settings": {"Place Range": "far"}},
              "KillAura": {"settings": {"Range": 3.0}}
            }}
            """);
        ModuleManager modules = modules();
        new ConfigManager(dir, modules).load();
        assertEquals(4.5, modules.get(AutoCrystal.class).placeRange.get());
        assertEquals(3.0, modules.get(dev.vanguard.module.modules.combat.KillAura.class).range.get());
    }
}
