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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persists module states, settings and binds, plus arbitrary GUI state, to
 * {@code .minecraft/vanguard/config.json}.
 *
 * <p>Named configs are snapshots of the modules alone, kept in {@code .minecraft/vanguard/configs}.
 * Loading one applies it on top of the live config, which is saved as usual.
 */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("Vanguard/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Longest name a saved config can have. */
    public static final int MAX_NAME_LENGTH = 24;
    private static final String EXTENSION = ".json";

    private final Path directory;
    private final Path file;
    private final Path configsDirectory;
    private final ModuleManager modules;
    private JsonObject guiState = new JsonObject();

    public ConfigManager(Path directory, ModuleManager modules) {
        this.directory = directory;
        this.file = directory.resolve("config.json");
        this.configsDirectory = directory.resolve("configs");
        this.modules = modules;
    }

    /** The folder everything is saved in. */
    public Path directory() {
        return directory;
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
        if (root.get("modules") instanceof JsonObject savedModules) applyModules(savedModules);
    }

    private void applyModules(JsonObject savedModules) {
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

    private JsonObject modulesToJson() {
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
        return savedModules;
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.add("modules", modulesToJson());
        root.add("gui", guiState);
        try {
            write(file, root);
        } catch (IOException e) {
            LOG.error("Failed to save {}", file, e);
        }
    }

    /** Writes to a temp file first, so a crash mid-write never leaves a truncated file. */
    private static void write(Path target, JsonObject root) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------ named configs

    /** The names of the saved configs, alphabetically. */
    public List<String> configNames() {
        if (!Files.isDirectory(configsDirectory)) return List.of();
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(configsDirectory)) {
            files.forEach(path -> {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(EXTENSION) && Files.isRegularFile(path)) {
                    names.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
                }
            });
        } catch (IOException e) {
            LOG.error("Failed to list {}", configsDirectory, e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * A name that's safe as a file name on every system: letters, digits, spaces, dashes and
     * underscores, trimmed and cut to {@link #MAX_NAME_LENGTH}. Empty if nothing usable is left.
     */
    public static String cleanName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (cleaned.length() > MAX_NAME_LENGTH) cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        return cleaned;
    }

    private Path configFile(String name) {
        return configsDirectory.resolve(name + EXTENSION);
    }

    /** Saves every module's state as the named config, replacing one with the same name. */
    public boolean saveConfig(String name) {
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) return false;
        JsonObject root = new JsonObject();
        root.add("modules", modulesToJson());
        try {
            write(configFile(cleaned), root);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to save config {}", cleaned, e);
            return false;
        }
    }

    /** Applies the named config to every module. Returns false if it's missing or unreadable. */
    public boolean loadConfig(String name) {
        Path path = configFile(cleanName(name));
        if (!Files.isRegularFile(path)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!(root.get("modules") instanceof JsonObject savedModules)) return false;
            applyModules(savedModules);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load config {}", name, e);
            return false;
        }
    }

    public boolean deleteConfig(String name) {
        try {
            return Files.deleteIfExists(configFile(cleanName(name)));
        } catch (IOException e) {
            LOG.error("Failed to delete config {}", name, e);
            return false;
        }
    }
}
