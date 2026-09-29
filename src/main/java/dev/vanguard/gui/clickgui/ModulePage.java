package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.clickgui.widget.KeybindWidget;
import dev.vanguard.gui.clickgui.widget.Widget;
import dev.vanguard.gui.clickgui.widget.Widgets;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Module;
import dev.vanguard.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A module's page: its key first, then its settings in their sections. The on/off switch lives in
 * the pane's header.
 */
final class ModulePage implements Page {
    private final Module module;
    private final List<Section> sections = new ArrayList<>();

    ModulePage(Module module) {
        this.module = module;
        sections.add(new Section("General", List.of(new KeybindWidget(module.bind()))));

        Map<String, List<Widget>> grouped = new LinkedHashMap<>();
        for (Setting<?> setting : module.settings()) {
            grouped.computeIfAbsent(setting.section(), name -> new ArrayList<>()).add(Widgets.create(setting));
        }
        grouped.forEach((title, widgets) -> sections.add(new Section(title, widgets)));
    }

    @Override
    public Module module() {
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

    @Override
    public void icon(Render2D render, float cx, float cy, float size, int color) {
        Icons.module(render, module, cx, cy, size, color);
    }
}
