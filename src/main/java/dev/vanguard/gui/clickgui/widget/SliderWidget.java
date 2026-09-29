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

/** Label and value on one line, a full-width track below. Drag to set, right-click to reset. */
public final class SliderWidget extends Widget {
    private static final float HEIGHT = 32f;
    private static final float LABEL_HEIGHT = 16f;
    private static final float TRACK_Y = 19f;
    private static final float TRACK_H = 3.2f;
    private static final float KNOB_W = 5f;
    private static final float KNOB_H = 6f;

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
            ctx.tooltip(description());
            ctx.cursor(CursorTypes.RESIZE_EW);
        }
        hover.animateTo(hovered || dragging ? 1 : 0);

        if (dragging) {
            number.setProgress((ctx.rawMouseX - trackX()) / trackWidth());
            fill.snap((float) number.progress());
        } else {
            fill.animateTo((float) number.progress());
        }

        drawHover(ctx, HEIGHT, hover.get());
        String value = number.format();
        float valueWidth = r.smallWidth(value);
        drawLabel(ctx, y, LABEL_HEIGHT, valueWidth, Theme.TEXT);
        r.small(value, x + width - PAD_X - valueWidth, r.smallY(y, LABEL_HEIGHT), Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hover.get()));

        float tx = trackX(), tw = trackWidth(), ty = y + TRACK_Y;
        r.roundedRect(tx, ty, tw, TRACK_H, TRACK_H / 2f, Theme.TRACK);
        float filled = tw * fill.get();
        int accent = ctx.theme.accent();
        if (filled > 0.5f) r.roundedRect(tx, ty, Math.max(filled, TRACK_H), TRACK_H, TRACK_H / 2f, accent);
        float kx = Math.clamp(tx + filled - KNOB_W / 2f, tx, tx + tw - KNOB_W);
        float ky = ty + TRACK_H / 2f - KNOB_H / 2f;
        r.roundedRect(kx, ky, KNOB_W, KNOB_H, 1.4f, Colors.lerp(accent, 0xFFFFFFFF, 0.15f * hover.get()));
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
