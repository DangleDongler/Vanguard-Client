package dev.vanguard.gui.clickgui.widget;

import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.ColorSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.KeybindSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.setting.Setting;

public final class Widgets {
    /** Visual height of capital letters, used to center text in a row. */
    private static final float CAP_HEIGHT = 7f;

    private Widgets() {
    }

    public static Widget create(Setting<?> setting) {
        return switch (setting) {
            case BoolSetting s -> new BoolWidget(s);
            case NumberSetting s -> new SliderWidget(s);
            case EnumSetting<?> s -> new EnumWidget(s);
            case ColorSetting s -> new ColorWidget(s);
            case KeybindSetting s -> new KeybindWidget(s);
            default -> throw new IllegalArgumentException("No widget for " + setting.getClass().getSimpleName());
        };
    }

    /** Y to draw text at so it sits vertically centered in a row. */
    public static float textY(float rowY, float rowHeight) {
        return rowY + (rowHeight - CAP_HEIGHT) / 2f;
    }
}
