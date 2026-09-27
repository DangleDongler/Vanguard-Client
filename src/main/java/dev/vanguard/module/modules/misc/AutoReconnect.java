package dev.vanguard.module.modules.misc;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;

// Settings only: reconnect logic lands in a later milestone.
public final class AutoReconnect extends Module {
    public final NumberSetting delay = number("Delay", "Seconds to wait before reconnecting.", 5, 1, 60, 1, "s");
    public final BoolSetting button = bool("Button", "Show a reconnect button on the disconnect screen.", true);

    public AutoReconnect() {
        super("AutoReconnect", "Reconnects after being kicked.", Category.MISC);
    }
}
