package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.clickgui.widget.BoolWidget;
import dev.vanguard.gui.clickgui.widget.KeybindWidget;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.clickgui.widget.Widgets;
import dev.vanguard.module.Module;
import dev.vanguard.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A module's page: a Main section with its switch and key, then its settings in their sections. */
final class ModulePage implements Page {
    private final Module module;
    private final List<Section> sections = new ArrayList<>();

    ModulePage(Module module) {
        this.module = module;

        List<Widget> main = new ArrayList<>();
        // The ClickGUI module is on exactly while this menu is open, so it has no switch.
        if (module.persistsEnabledState()) {
            main.add(new BoolWidget("Toggle", "Turn " + module.name() + " on or off.", module::isEnabled, module::toggle));
        }
        main.add(new KeybindWidget(module.bind()));
        sections.add(new Section("Main", main));

        Map<String, List<Widget>> grouped = new LinkedHashMap<>();
        for (Setting<?> setting : module.settings()) {
            grouped.computeIfAbsent(setting.section(), name -> new ArrayList<>()).add(Widgets.create(setting));
        }
        grouped.forEach((title, widgets) -> sections.add(new Section(title, widgets)));
    }

    Module module() {
        return module;
    }

    @Override
    public String title() {
        return module.name();
    }

    @Override
    public String description() {
        return module.description();
    }

    @Override
    public List<Section> sections() {
        return sections;
    }
}
