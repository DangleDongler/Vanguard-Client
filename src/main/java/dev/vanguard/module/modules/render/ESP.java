package dev.vanguard.module.modules.render;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: world rendering lands in a later milestone.
public final class ESP extends Module {
    public enum Mode { BOX, OUTLINE, GLOW }

    public final EnumSetting<Mode> mode = mode("Mode", "How entities are highlighted.", Mode.OUTLINE);
    public final BoolSetting players = bool("Players", "Highlight players.", true);
    public final ColorSetting playerColor = color("Player Color", "Player highlight color.", 0xFFFF4D6D).visibleWhen(players::isOn);
    public final BoolSetting crystals = bool("Crystals", "Highlight end crystals.", true);
    public final BoolSetting items = bool("Items", "Highlight dropped items.", false);
    public final NumberSetting lineWidth = number("Line Width", "Box line width.", 1.5, 0.5, 5, 0.5)
        .visibleWhen(() -> this.mode.is(Mode.BOX));

    public ESP() {
        super("ESP", "Highlights entities through walls.", Category.RENDER);
    }
}
