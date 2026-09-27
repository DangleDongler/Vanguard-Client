package dev.vanguard.module.modules.player;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: interaction logic lands in a later milestone.
public final class FastPlace extends Module {
    public final NumberSetting delay = number("Delay", "Ticks between uses.", 0, 0, 4, 1, "t");
    public final BoolSetting crystalsOnly = bool("Crystals Only", "Only affect end crystals and XP bottles.", false);

    public FastPlace() {
        super("FastPlace", "Removes the item use delay.", Category.PLAYER);
    }
}
