package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: inventory logic lands in a later milestone.
public final class AutoTotem extends Module {
    public enum Mode { STRICT, SMART }

    public final EnumSetting<Mode> mode = mode("Mode", "Strict always holds a totem; Smart only below the health threshold.", Mode.STRICT);
    public final NumberSetting health = number("Health", "Health at which Smart mode switches to a totem.", 14, 1, 36, 0.5)
        .visibleWhen(() -> this.mode.is(Mode.SMART));
    public final NumberSetting delay = number("Delay", "Ticks to wait between inventory clicks.", 0, 0, 10, 1, "t");

    public AutoTotem() {
        super("AutoTotem", "Keeps a totem of undying in your offhand.", Category.COMBAT);
    }
}
