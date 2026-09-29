package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** A text box and a button that submits it. Enter submits too; Escape stops typing. */
public final class TextFieldWidget extends Widget {
    private static final float BOX_H = 14f;
    private static final float GAP = 3f;

    private final String placeholder;
    private final String buttonLabel;
    private final int maxLength;
    private final Consumer<String> submit;
    private final StringBuilder text = new StringBuilder();
    private final Animation focus = new Animation(0, 160, Easing.CUBIC_OUT);
    private final Animation buttonHover = new Animation(0, 120, Easing.LINEAR);
    private boolean focused;
    private long lastEdit;
    private float buttonX, buttonW;

    public TextFieldWidget(String placeholder, String buttonLabel, String description, int maxLength, Consumer<String> submit) {
        super(null, placeholder, description);
        this.placeholder = placeholder;
        this.buttonLabel = buttonLabel;
        this.maxLength = maxLength;
        this.submit = submit;
    }

    public void clear() {
        text.setLength(0);
    }

    @Override
    public float height() {
        return ROW_HEIGHT;
    }

    @Override
    protected void draw(GuiContext ctx) {
        Render2D r = ctx.render;
        float by = y + (ROW_HEIGHT - BOX_H) / 2f;
        buttonW = r.smallWidth(buttonLabel) + 14f;
        buttonX = x + width - PAD_X - buttonW;
        float fieldX = x + PAD_X, fieldW = buttonX - GAP - fieldX;

        boolean overField = ctx.hovered(fieldX, by, fieldW, BOX_H);
        boolean overButton = ctx.hovered(buttonX, by, buttonW, BOX_H);
        if (overField) ctx.cursor(CursorTypes.IBEAM);
        if (overButton) ctx.cursor(CursorTypes.POINTING_HAND);
        if (overField || overButton) ctx.tooltip(description());
        focus.animateTo(focused ? 1 : 0);
        buttonHover.animateTo(overButton ? 1 : 0);

        float f = focus.get();
        r.roundedRect(fieldX, by, fieldW, BOX_H, 3f, Theme.FIELD);
        if (f > 0.01f) r.roundedOutline(fieldX, by, fieldW, BOX_H, 3f, 0.6f, Colors.fade(0x40FFFFFF, f));
        float textY = r.smallY(by, BOX_H);
        if (text.isEmpty() && !focused) {
            r.small(placeholder, fieldX + 7f, textY, Theme.TEXT_MUTED);
        } else {
            String shown = text.toString();
            // Keep the end of long text in view.
            while (!shown.isEmpty() && r.smallWidth(shown) > fieldW - 16f) shown = shown.substring(1);
            float drawn = r.small(shown, fieldX + 7f, textY, Theme.TEXT);
            boolean caretOn = System.currentTimeMillis() - lastEdit < 500 || (System.currentTimeMillis() / 530) % 2 == 0;
            if (focused && caretOn) r.rect(fieldX + 7f + drawn + 0.8f, by + 3.5f, 0.7f, BOX_H - 7f, Theme.TEXT);
        }

        r.roundedRect(buttonX, by, buttonW, BOX_H, 3f, Colors.lerp(Theme.BUTTON, Theme.BUTTON_HOVER, buttonHover.get()));
        r.small(buttonLabel, buttonX + 7f, textY, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, buttonHover.get()));
    }

    private void submit() {
        String value = text.toString().trim();
        if (value.isEmpty()) return;
        submit.accept(value);
    }

    @Override
    public void clickedOutside() {
        focused = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inRow(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        float by = y + (ROW_HEIGHT - BOX_H) / 2f;
        if (contains(mouseX, mouseY, buttonX, by, buttonW, BOX_H)) {
            submit();
        } else {
            focused = true;
            lastEdit = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> focused = false;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submit();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (control) text.setLength(0);
                else if (!text.isEmpty()) text.deleteCharAt(text.length() - 1);
            }
            case GLFW.GLFW_KEY_V -> {
                if (!control) return false;
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("\\p{Cntrl}", "");
                text.append(clip, 0, Math.min(clip.length(), maxLength - text.length()));
            }
            default -> {
                // Printable keys arrive as characters instead.
                return false;
            }
        }
        lastEdit = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (!focused) return false;
        if (text.length() < maxLength && !Character.isISOControl(codepoint)) {
            text.appendCodePoint(codepoint);
            lastEdit = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean isCapturingKeyboard() {
        return focused;
    }
}
