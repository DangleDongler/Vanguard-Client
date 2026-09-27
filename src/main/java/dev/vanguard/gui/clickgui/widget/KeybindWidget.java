package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.KeybindSetting;
import dev.vanguard.util.Keys;
import org.lwjgl.glfw.GLFW;

/** Click to listen, then press a key. Escape cancels; Backspace, Delete or right-click unbinds. */
public final class KeybindWidget extends Widget {
    private final KeybindSetting bind;
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);
    private final Animation listen = new Animation(0, 160, Easing.CUBIC_OUT);
    private boolean listening;
    private boolean wasListening;

    public KeybindWidget(KeybindSetting setting) {
        super(setting);
        this.bind = setting;
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
            ctx.tooltip(setting.description() + " Right-click to clear.");
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);
        listen.animateTo(listening ? 1 : 0);

        r.rect(x, y, width, ROW_HEIGHT, Colors.fade(Theme.HOVER, hover.get()));
        String label = listening ? "..." : Keys.name(bind.key());
        float boxW = Math.max(18f, r.textWidth(label) + 8f);
        drawLabel(ctx, Widgets.textY(y, ROW_HEIGHT), boxW, Theme.TEXT);
        float boxH = 10f;
        float bx = x + width - PAD_X - boxW;
        float by = y + (ROW_HEIGHT - boxH) / 2f;
        float l = listen.get();
        r.roundedRect(bx, by, boxW, boxH, 3f, Colors.lerp(Theme.FIELD, ctx.theme.accent(40), l));
        r.roundedOutline(bx, by, boxW, boxH, 3f, 0.6f, Colors.lerp(Theme.OUTLINE, ctx.theme.accent(), l));

        int textColor = bind.isBound() ? Theme.TEXT : Theme.TEXT_MUTED;
        if (listening) {
            // Pulse while waiting for a key.
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 160.0);
            textColor = Colors.fade(ctx.theme.accent(), pulse);
        }
        r.textCentered(label, bx + boxW / 2f, Widgets.textY(by, boxH), textColor, false);
    }

    @Override
    public void clickedOutside() {
        wasListening = listening;
        listening = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inRow(mouseX, mouseY)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Clicking the row again while listening cancels.
            listening = !wasListening;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            listening = false;
            bind.set(Keys.NONE);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        if (!listening) return false;
        listening = false;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> { }
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> bind.set(Keys.NONE);
            default -> bind.set(key);
        }
        return true;
    }

    @Override
    public boolean isCapturingKeyboard() {
        return listening;
    }
}
