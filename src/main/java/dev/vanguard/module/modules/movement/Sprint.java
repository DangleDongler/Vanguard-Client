package dev.vanguard.module.modules.movement;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;

// Settings only: movement logic lands in a later milestone.
public final class Sprint extends Module {
    public enum Mode { LEGIT, RAGE }

    public final EnumSetting<Mode> mode = mode("Mode", "Rage sprints in every direction.", Mode.LEGIT);
    public final BoolSetting keepSprint = bool("Keep Sprint", "Don't stop sprinting after attacking.", true);

    public Sprint() {
        super("Sprint", "Sprints automatically.", Category.MOVEMENT);
    }
}
