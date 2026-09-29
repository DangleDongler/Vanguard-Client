package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/** A label and a switch. Click anywhere on the row to flip it. */
public final class BoolWidget extends Widget {
    private static final float SWITCH_W = 22f;
    private static final float SWITCH_H = 12.5f;

    private final BooleanSupplier value;
    private final Runnable toggle;
    private final Animation state;
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);

    public BoolWidget(BoolSetting setting) {
        super(setting);
        this.value = setting::isOn;
        this.toggle = setting::toggle;
        this.state = new Animation(setting.isOn() ? 1 : 0, 260, Easing.CUBIC_OUT);
    }

    /** A switch that isn't a setting, such as a module's own on/off. */
    public BoolWidget(String label, String description, BooleanSupplier value, Runnable toggle) {
        super(null, label, description);
        this.value = value;
        this.toggle = toggle;
        this.state = new Animation(value.getAsBoolean() ? 1 : 0, 260, Easing.CUBIC_OUT);
    }

    @Override
    public float height() {
        return ROW_HEIGHT;
    }

    @Override
    protected void draw(GuiContext ctx) {
        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        if (hovered) {
            ctx.tooltip(description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);
        state.animateTo(value.getAsBoolean() ? 1 : 0);

        drawHover(ctx, ROW_HEIGHT, hover.get());
        drawLabel(ctx, y, ROW_HEIGHT, SWITCH_W, Theme.TEXT);
        float sx = x + width - PAD_X - SWITCH_W;
        float sy = y + (ROW_HEIGHT - SWITCH_H) / 2f;
        Controls.drawSwitch(ctx, sx, sy, SWITCH_W, SWITCH_H, state.get(), hover.get());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !inRow(mouseX, mouseY)) return false;
        toggle.run();
        return true;
    }
}
