package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class SliderWidget extends Widget {
    private static final float HEIGHT = 21f;
    private static final float TRACK_H = 3f;

    private final NumberSetting number;
    private final Animation fill;
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);
    private boolean dragging;

    public SliderWidget(NumberSetting setting) {
        super(setting);
        this.number = setting;
        this.fill = new Animation((float) setting.progress(), 160, Easing.CUBIC_OUT);
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    private float trackX() {
        return x + PAD_X;
    }

    private float trackWidth() {
        return width - PAD_X * 2;
    }

    @Override
    protected void draw(GuiContext ctx) {
        Render2D r = ctx.render;
        boolean hovered = ctx.hovered(x, y, width, HEIGHT);
        if (hovered || dragging) {
            ctx.tooltip(setting.description());
            ctx.cursor(CursorTypes.RESIZE_EW);
        }
        hover.animateTo(hovered || dragging ? 1 : 0);

        if (dragging) {
            number.setProgress((ctx.rawMouseX - trackX()) / trackWidth());
            fill.snap((float) number.progress());
        } else {
            fill.animateTo((float) number.progress());
        }

        r.rect(x, y, width, HEIGHT, Colors.fade(Theme.HOVER, hover.get()));
        float textY = y + 3.5f;
        String value = number.format();
        float valueWidth = r.textWidth(value);
        drawLabel(ctx, textY, valueWidth, Theme.TEXT);
        r.text(value, x + width - PAD_X - valueWidth, textY, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover.get()));

        float tx = trackX(), tw = trackWidth(), ty = y + HEIGHT - 6f;
        float progress = fill.get();
        r.roundedRect(tx, ty, tw, TRACK_H, TRACK_H / 2f, Theme.TRACK);
        float filled = tw * progress;
        if (filled > 0.5f) {
            r.roundedRect(tx, ty, Math.max(filled, TRACK_H), TRACK_H, TRACK_H / 2f, ctx.theme.accent());
        }
        float knob = 2.6f + 0.9f * hover.get();
        float kx = tx + filled;
        r.shadow(kx - knob, ty + TRACK_H / 2f - knob, knob * 2, knob * 2, knob, 3f, 0x66000000);
        r.circle(kx, ty + TRACK_H / 2f, knob, Theme.KNOB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY, x, y, width, HEIGHT)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            number.setProgress((mouseX - trackX()) / trackWidth());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            number.reset();
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
    }
}
