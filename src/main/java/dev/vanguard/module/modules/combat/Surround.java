package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: placement logic lands in a later milestone.
public final class Surround extends Module {
    public final BoolSetting center = bool("Center", "Snap to the center of the block first.", true);
    public final BoolSetting onlyOnGround = bool("Only On Ground", "Wait until you are on the ground.", true);
    public final NumberSetting blocksPerTick = number("Blocks/Tick", "Blocks placed each tick.", 4, 1, 8, 1);
    public final BoolSetting disableOnJump = bool("Disable On Jump", "Turn off when you jump.", true);
    public final BoolSetting render = bool("Render", "Highlight placed blocks.", true);
    public final ColorSetting renderColor = color("Render Color", "Highlight color.", 0x6038D39F).visibleWhen(render::isOn);

    public Surround() {
        super("Surround", "Surrounds your feet with obsidian.", Category.COMBAT);
    }
}
