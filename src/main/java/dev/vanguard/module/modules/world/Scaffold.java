package dev.vanguard.module.modules.world;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: placement logic lands in a later milestone.
public final class Scaffold extends Module {
    public enum Swap { OFF, NORMAL, SILENT }

    public final BoolSetting tower = bool("Tower", "Build straight up quickly while jumping.", true);
    public final BoolSetting rotate = bool("Rotate", "Face the placement server-side.", true);
    public final EnumSetting<Swap> swap = mode("Swap", "How to switch to blocks.", Swap.SILENT);
    public final NumberSetting extend = number("Extend", "Place this many blocks ahead.", 0, 0, 5, 1);

    public Scaffold() {
        super("Scaffold", "Places blocks under you.", Category.WORLD);
    }
}
