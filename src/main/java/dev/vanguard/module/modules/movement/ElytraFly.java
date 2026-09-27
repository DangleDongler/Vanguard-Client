package dev.vanguard.module.modules.movement;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: flight logic lands in a later milestone.
public final class ElytraFly extends Module {
    public enum Mode { BOUNCE, CONTROL, PACKET }

    public final EnumSetting<Mode> mode = mode("Mode", "Flight method.", Mode.BOUNCE);
    public final NumberSetting speed = number("Speed", "Horizontal speed.", 1.8, 0.1, 5, 0.05);
    public final NumberSetting verticalSpeed = number("Vertical Speed", "Ascend and descend speed.", 1, 0.1, 5, 0.05)
        .visibleWhen(() -> !this.mode.is(Mode.BOUNCE));
    public final NumberSetting pitch = number("Pitch", "Pitch held while bouncing.", 75, 0, 90, 1, "°")
        .visibleWhen(() -> this.mode.is(Mode.BOUNCE));
    public final BoolSetting autoTakeoff = bool("Auto Takeoff", "Open the elytra automatically when falling.", true);

    public ElytraFly() {
        super("ElytraFly", "Improved elytra flight.", Category.MOVEMENT);
    }
}
