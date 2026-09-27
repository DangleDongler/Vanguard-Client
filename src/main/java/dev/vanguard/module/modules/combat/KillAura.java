package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: combat logic lands in a later milestone.
public final class KillAura extends Module {
    public enum Targets { PLAYERS, HOSTILES, ALL }

    public enum Priority { DISTANCE, HEALTH, ANGLE }

    public final NumberSetting range = number("Range", "Attack reach.", 4.2, 1, 6, 0.1, "m");
    public final EnumSetting<Targets> targets = mode("Targets", "Which entities to attack.", Targets.PLAYERS);
    public final EnumSetting<Priority> priority = mode("Priority", "How to pick between targets.", Priority.DISTANCE);
    public final BoolSetting rotate = bool("Rotate", "Face the target server-side before attacking.", true);
    public final BoolSetting cooldown = bool("Wait Cooldown", "Only attack at full attack strength.", true);
    public final BoolSetting weaponOnly = bool("Weapon Only", "Only attack while holding a sword or axe.", false);

    public KillAura() {
        super("KillAura", "Attacks nearby entities.", Category.COMBAT);
    }
}
