package dev.vanguard.module.modules.movement;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: packet logic lands in a later milestone.
public final class Velocity extends Module {
    public final NumberSetting horizontal = number("Horizontal", "Horizontal knockback taken.", 0, 0, 100, 1, "%");
    public final NumberSetting vertical = number("Vertical", "Vertical knockback taken.", 0, 0, 100, 1, "%");
    public final BoolSetting explosions = bool("Explosions", "Also reduce explosion knockback.", true);
    public final BoolSetting pushing = bool("No Push", "Don't get pushed by entities or water.", true);

    public Velocity() {
        super("Velocity", "Reduces knockback.", Category.MOVEMENT);
    }
}
