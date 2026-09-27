package dev.vanguard.module.modules.render;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: world rendering lands in a later milestone.
public final class HoleESP extends Module {
    public final NumberSetting range = number("Range", "Search radius.", 8, 1, 20, 1, "m");
    public final ColorSetting bedrock = color("Bedrock", "Color of fully bedrock holes.", 0x5538D39F);
    public final ColorSetting obsidian = color("Obsidian", "Color of holes with obsidian.", 0x55FF4D6D);
    public final NumberSetting height = number("Height", "Box height.", 0.1, 0, 1, 0.05);
    public final BoolSetting fill = bool("Fill", "Fill the box.", true);
    public final BoolSetting outline = bool("Outline", "Draw the box outline.", true);

    public HoleESP() {
        super("HoleESP", "Shows safe holes nearby.", Category.RENDER);
    }
}
