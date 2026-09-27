package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;

public final class BoolWidget extends Widget {
    private static final float SWITCH_W = 15f;
    private static final float SWITCH_H = 8f;

    private final BoolSetting bool;
    private final Animation state;
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);

    public BoolWidget(BoolSetting setting) {
        super(setting);
        this.bool = setting;
        this.state = new Animation(setting.isOn() ? 1 : 0, 200, Easing.CUBIC_OUT);
    }

    @Override
    public float height() {
        return ROW_HEIGHT;
    }

    @Override
    protected void draw(GuiContext ctx) {
        Render2D r = ctx.render;
        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        if (hovered) {
            ctx.tooltip(setting.description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);
        state.animateTo(bool.isOn() ? 1 : 0);
        float t = state.get();

        r.rect(x, y, width, ROW_HEIGHT, Colors.fade(Theme.HOVER, hover.get()));
        drawLabel(ctx, Widgets.textY(y, ROW_HEIGHT), SWITCH_W, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, t));

        float sx = x + width - PAD_X - SWITCH_W;
        float sy = y + (ROW_HEIGHT - SWITCH_H) / 2f;
        r.roundedRect(sx, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2f, Colors.lerp(Theme.TRACK, ctx.theme.accent(), t));
        if (t > 0.01f) {
            r.shadow(sx, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2f, 4f, ctx.theme.accent(Math.round(70 * t)));
        }
        float knobRadius = SWITCH_H / 2f - 1.5f;
        float knobX = sx + SWITCH_H / 2f + (SWITCH_W - SWITCH_H) * t;
        r.circle(knobX, sy + SWITCH_H / 2f, knobRadius, Colors.lerp(0xFFB9B9C6, Theme.KNOB, t));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !inRow(mouseX, mouseY)) return false;
        bool.toggle();
        return true;
    }
}
