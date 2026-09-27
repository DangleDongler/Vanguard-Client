package dev.vanguard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanguard.module.Module;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persists module states, settings and binds, plus arbitrary GUI state, to
 * {@code .minecraft/vanguard/config.json}.
 */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("Vanguard/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final ModuleManager modules;
    private JsonObject guiState = new JsonObject();

    public ConfigManager(Path directory, ModuleManager modules) {
        this.file = directory.resolve("config.json");
        this.modules = modules;
    }

    /** Free-form section owned by the GUI (panel positions etc.). Mutations are saved with {@link #save()}. */
    public JsonObject guiState() {
        return guiState;
    }

    public void load() {
        if (!Files.exists(file)) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to read {}, keeping defaults", file, e);
            return;
        }

        if (root.get("gui") instanceof JsonObject gui) guiState = gui;
        if (!(root.get("modules") instanceof JsonObject savedModules)) return;

        for (Module module : modules.all()) {
            if (!(savedModules.get(module.name()) instanceof JsonObject saved)) continue;
            try {
                if (saved.get("settings") instanceof JsonObject settings) {
                    for (Setting<?> setting : module.settings()) {
                        JsonElement value = settings.get(setting.name());
                        if (value != null) setting.fromJson(value);
                    }
                }
                if (saved.has("bind")) module.bind().fromJson(saved.get("bind"));
                if (module.persistsEnabledState() && saved.has("enabled")) {
                    module.setEnabled(saved.get("enabled").getAsBoolean());
                }
            } catch (RuntimeException e) {
                LOG.warn("Skipping malformed config for {}", module.name(), e);
            }
        }
    }

    public void save() {
        JsonObject savedModules = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject saved = new JsonObject();
            if (module.persistsEnabledState()) saved.addProperty("enabled", module.isEnabled());
            saved.add("bind", module.bind().toJson());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) settings.add(setting.name(), setting.toJson());
            saved.add("settings", settings);
            savedModules.add(module.name(), saved);
        }

        JsonObject root = new JsonObject();
        root.add("modules", savedModules);
        root.add("gui", guiState);

        try {
            Files.createDirectories(file.getParent());
            // Write to a temp file first so a crash mid-write never leaves a truncated config.
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Failed to save {}", file, e);
        }
    }
}
