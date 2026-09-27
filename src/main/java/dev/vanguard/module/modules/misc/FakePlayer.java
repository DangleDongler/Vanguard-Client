package dev.vanguard.module.modules.misc;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: entity logic lands in a later milestone.
public final class FakePlayer extends Module {
    public final BoolSetting copyInventory = bool("Copy Inventory", "Give the fake player your armor and items.", true);
    public final NumberSetting health = number("Health", "Starting health.", 36, 1, 36, 1);

    public FakePlayer() {
        super("FakePlayer", "Spawns a client-side player to test combat modules on.", Category.MISC);
    }
}
