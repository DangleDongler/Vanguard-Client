package dev.vanguard.module.modules.player;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: eating logic lands in a later milestone.
public final class AutoEat extends Module {
    public final NumberSetting hunger = number("Hunger", "Eat when hunger drops to this.", 16, 1, 19, 1);
    public final NumberSetting health = number("Health", "Eat a golden apple below this health.", 10, 1, 36, 0.5);
    public final BoolSetting gapplesOnly = bool("Gapples Only", "Only eat golden apples.", false);
    public final BoolSetting pauseCombat = bool("Pause Combat", "Pause combat modules while eating.", true);

    public AutoEat() {
        super("AutoEat", "Eats automatically.", Category.PLAYER);
    }
}
