package dev.vanguard.module.modules.world;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: mining logic lands in a later milestone.
public final class Nuker extends Module {
    public enum Mode { ALL, FLATTEN, SMASH }

    public final EnumSetting<Mode> mode = mode("Mode", "Which blocks to break.", Mode.FLATTEN);
    public final NumberSetting range = number("Range", "Break radius.", 4.5, 1, 6, 0.1, "m");
    public final NumberSetting blocksPerTick = number("Blocks/Tick", "Blocks broken each tick.", 1, 1, 10, 1);
    public final BoolSetting swing = bool("Swing", "Swing your hand client-side.", true);

    public Nuker() {
        super("Nuker", "Breaks blocks around you.", Category.WORLD);
    }
}
