package dev.vanguard.module;

import dev.vanguard.module.modules.client.ClickGuiModule;
import dev.vanguard.module.modules.combat.AutoCrystal;
import dev.vanguard.module.modules.combat.AutoTotem;
import dev.vanguard.module.modules.combat.AimAssist;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import dev.vanguard.module.modules.combat.Surround;
import dev.vanguard.module.modules.combat.TriggerBot;
import dev.vanguard.module.modules.misc.AutoReconnect;
import dev.vanguard.module.modules.misc.FakePlayer;
import dev.vanguard.module.modules.movement.ElytraFly;
import dev.vanguard.module.modules.movement.Sprint;
import dev.vanguard.module.modules.movement.Velocity;
import dev.vanguard.module.modules.player.AutoEat;
import dev.vanguard.module.modules.player.FastPlace;
import dev.vanguard.module.modules.render.ESP;
import dev.vanguard.module.modules.render.Fullbright;
import dev.vanguard.module.modules.render.HoleESP;
import dev.vanguard.module.modules.world.Nuker;
import dev.vanguard.module.modules.world.Scaffold;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    public void init() {
        register(new AutoCrystal());
        register(new AimAssist());
        register(new TriggerBot());
        register(new ShieldBreaker());
        register(new AutoTotem());
        register(new Surround());

        register(new ElytraFly());
        register(new Sprint());
        register(new Velocity());

        register(new ESP());
        register(new HoleESP());
        register(new Fullbright());

        register(new AutoEat());
        register(new FastPlace());

        register(new Scaffold());
        register(new Nuker());

        register(new AutoReconnect());
        register(new FakePlayer());

        register(new ClickGuiModule());

        byCategory.values().forEach(list -> list.sort(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER)));
    }

    private void register(Module module) {
        modules.add(module);
        byCategory.computeIfAbsent(module.category(), c -> new ArrayList<>()).add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> inCategory(Category category) {
        return Collections.unmodifiableList(byCategory.getOrDefault(category, List.of()));
    }

    public <M extends Module> M get(Class<M> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) return type.cast(module);
        }
        throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
    }

    public Module byName(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Module module : modules) {
            if (module.name().toLowerCase(Locale.ROOT).equals(wanted)) return module;
        }
        return null;
    }

    /** Toggles every module bound to {@code key}. Called only while no screen is open. */
    public void onKeyPressed(int key) {
        for (Module module : modules) {
            if (module.bind().matches(key)) module.toggle();
        }
    }
}
