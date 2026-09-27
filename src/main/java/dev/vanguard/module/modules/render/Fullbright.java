package dev.vanguard.module.modules.render;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.EnumSetting;

// Settings only: lighting logic lands in a later milestone.
public final class Fullbright extends Module {
    public enum Mode { GAMMA, NIGHT_VISION }

    public final EnumSetting<Mode> mode = mode("Mode", "How brightness is raised.", Mode.GAMMA);

    public Fullbright() {
        super("Fullbright", "Lights up dark areas.", Category.RENDER);
    }
}
