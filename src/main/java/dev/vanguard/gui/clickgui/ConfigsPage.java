package dev.vanguard.gui.clickgui;

import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.clickgui.widget.ActionsWidget;
import dev.vanguard.gui.clickgui.widget.ActionsWidget.Action;
import dev.vanguard.gui.clickgui.widget.NoteWidget;
import dev.vanguard.gui.clickgui.widget.TextFieldWidget;
import dev.vanguard.gui.clickgui.widget.Widget;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Named configs: save the current setup under a name, then load, update or delete it later. */
final class ConfigsPage implements Page {
    private static final String DESCRIPTION = "Save your modules and settings as a named config, and load it back any time.";
    private static final long STATUS_MS = 3000;

    private final ConfigManager config;
    private final TextFieldWidget nameField;
    private final Section saved = new Section("Saved", List.of());
    private final List<Section> sections = new ArrayList<>();
    private String status;
    private long statusAt;

    ConfigsPage(ConfigManager config) {
        this.config = config;
        this.nameField = new TextFieldWidget("Config name", "Save",
            "Saves every module's settings, key and on/off state under this name.", ConfigManager.MAX_NAME_LENGTH, this::saveNew);
        sections.add(new Section("New Config", List.of(nameField)));
        sections.add(saved);
        sections.add(new Section("Folder", List.of(new ActionsWidget("Config folder", "Where configs are saved.", Theme.TEXT,
            new Action("Open", "Open the folder in your file browser.", false, () -> Util.getPlatform().openPath(config.directory()))))));
    }

    @Override
    public String title() {
        return "Configs";
    }

    @Override
    public String description() {
        return status != null && System.currentTimeMillis() - statusAt < STATUS_MS ? status : DESCRIPTION;
    }

    @Override
    public List<Section> sections() {
        return sections;
    }

    @Override
    public void onShow() {
        refresh();
    }

    private void refresh() {
        List<Widget> rows = new ArrayList<>();
        for (String name : config.configNames()) {
            rows.add(new ActionsWidget(name, "Config “" + name + "”", Theme.TEXT,
                new Action("Load", "Switch to this config.", false, () -> load(name)),
                new Action("Update", "Save your current settings into this config.", false, () -> update(name)),
                new Action("Delete", "Delete this config.", true, () -> delete(name))));
        }
        if (rows.isEmpty()) rows.add(new NoteWidget("No saved configs yet."));
        saved.setWidgets(rows);
    }

    private void status(String message) {
        status = message;
        statusAt = System.currentTimeMillis();
    }

    private void saveNew(String name) {
        String cleaned = ConfigManager.cleanName(name);
        if (cleaned.isEmpty()) {
            status("Use letters, numbers, spaces, dashes or underscores in the name.");
            return;
        }
        if (config.saveConfig(cleaned)) {
            status("Saved “" + cleaned + "”.");
            nameField.clear();
            refresh();
        } else {
            status("Couldn't save “" + cleaned + "”.");
        }
    }

    private void load(String name) {
        if (config.loadConfig(name)) {
            config.save();
            status("Loaded “" + name + "”.");
        } else {
            status("Couldn't load “" + name + "”.");
            refresh();
        }
    }

    private void update(String name) {
        status(config.saveConfig(name) ? "Updated “" + name + "”." : "Couldn't save “" + name + "”.");
    }

    private void delete(String name) {
        status(config.deleteConfig(name) ? "Deleted “" + name + "”." : "Couldn't delete “" + name + "”.");
        refresh();
    }
}
