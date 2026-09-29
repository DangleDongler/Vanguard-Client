package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.KeybindSetting;
import dev.vanguard.util.Keys;
import org.lwjgl.glfw.GLFW;

/**
 * The key in a box, and a button that clears it. Click the key to listen, then press a key.
 * Escape cancels; Backspace, Delete or right-click unbinds.
 */
public final class KeybindWidget extends Widget {
    private static final float BOX_H = 14f;
    private static final float GAP = 3f;

    private final KeybindSetting bind;
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);
    private final Animation clearHover = new Animation(0, 120, Easing.LINEAR);
    private final Animation listen = new Animation(0, 160, Easing.CUBIC_OUT);
    private boolean listening;
    private boolean wasListening;
    private float keyX, keyW, clearX;

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
        float boxY = y + (ROW_HEIGHT - BOX_H) / 2f;
        clearX = x + width - PAD_X - BOX_H;
        String label = listening ? "Press a key" : Keys.name(bind.key());
        keyW = Math.max(30f, r.smallWidth(label) + 16f);
        keyX = clearX - GAP - keyW;

        boolean overKey = ctx.hovered(keyX, boxY, keyW, BOX_H);
        boolean overClear = ctx.hovered(clearX, boxY, BOX_H, BOX_H);
        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        if (hovered) ctx.tooltip(overClear ? "Clear the key." : description() + " Right-click to clear.");
        if (overKey || overClear) ctx.cursor(CursorTypes.POINTING_HAND);
        hover.animateTo(overKey ? 1 : 0);
        clearHover.animateTo(overClear ? 1 : 0);
        listen.animateTo(listening ? 1 : 0);

        drawHover(ctx, ROW_HEIGHT, hovered ? 1 : 0);
        drawLabel(ctx, y, ROW_HEIGHT, keyW + GAP + BOX_H, Theme.TEXT);

        float l = listen.get();
        r.roundedRect(keyX, boxY, keyW, BOX_H, 3f, Colors.lerp(Colors.lerp(Theme.FIELD, Theme.BUTTON, hover.get() * 0.6f), Theme.BUTTON, l));
        if (l > 0.01f) r.roundedOutline(keyX, boxY, keyW, BOX_H, 3f, 0.6f, ctx.theme.accent(Math.round(140 * l)));
        int textColor = bind.isBound() ? Theme.TEXT : Theme.TEXT_MUTED;
        if (listening) {
            // Pulse while waiting for a key.
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 160.0);
            textColor = Colors.fade(Theme.TEXT, pulse);
        }
        float textWidth = r.smallWidth(label);
        r.small(label, keyX + (keyW - textWidth) / 2f, r.smallY(boxY, BOX_H), textColor);

        r.roundedRect(clearX, boxY, BOX_H, BOX_H, 3f, Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, clearHover.get()));
        Icons.close(r, clearX + BOX_H / 2f, boxY + BOX_H / 2f, 9f, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, clearHover.get()));
    }

    @Override
    public void clickedOutside() {
        wasListening = listening;
        listening = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inRow(mouseX, mouseY)) return false;
        float boxY = y + (ROW_HEIGHT - BOX_H) / 2f;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || contains(mouseX, mouseY, clearX, boxY, BOX_H, BOX_H)) {
            listening = false;
            bind.set(Keys.NONE);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        // Clicking the row again while listening cancels.
        listening = !wasListening;
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
