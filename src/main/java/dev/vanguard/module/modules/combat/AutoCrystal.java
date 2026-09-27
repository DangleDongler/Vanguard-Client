package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: combat logic lands in a later milestone.
public final class AutoCrystal extends Module {
    public enum Swap { OFF, NORMAL, SILENT }

    public final BoolSetting place = bool("Place", "Place end crystals near targets.", true);
    public final BoolSetting breakCrystals = bool("Break", "Break end crystals near targets.", true);
    public final NumberSetting targetRange = number("Target Range", "Maximum distance to look for targets.", 10, 4, 16, 0.5, "m");
    public final NumberSetting placeRange = number("Place Range", "Maximum crystal placement distance.", 4.5, 1, 6, 0.1, "m");
    public final NumberSetting breakRange = number("Break Range", "Maximum crystal break distance.", 4.5, 1, 6, 0.1, "m");
    public final NumberSetting minDamage = number("Min Damage", "Minimum damage dealt to a target.", 6, 0, 36, 0.5);
    public final NumberSetting maxSelfDamage = number("Max Self Damage", "Maximum damage you are willing to take.", 8, 0, 36, 0.5);
    public final BoolSetting antiSuicide = bool("Anti Suicide", "Never place a crystal that would kill you.", true);
    public final EnumSetting<Swap> swap = mode("Swap", "How to switch to crystals.", Swap.SILENT);
    public final NumberSetting placeDelay = number("Place Delay", "Ticks between placements.", 0, 0, 20, 1, "t");
    public final BoolSetting render = bool("Render", "Highlight the placement position.", true);
    public final ColorSetting renderColor = color("Render Color", "Highlight color.", 0x807B61FF).visibleWhen(render::isOn);

    public AutoCrystal() {
        super("AutoCrystal", "Automatically places and breaks end crystals.", Category.COMBAT);
    }
}
