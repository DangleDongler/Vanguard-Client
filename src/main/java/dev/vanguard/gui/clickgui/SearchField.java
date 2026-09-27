package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.widget.Widgets;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Module search. Typing anywhere in the ClickGUI goes here. */
final class SearchField {
    static final float WIDTH = 170f;
    static final float HEIGHT = 18f;
    private static final int MAX_LENGTH = 32;

    private final StringBuilder text = new StringBuilder();
    private final Animation active = new Animation(0, 180, Easing.CUBIC_OUT);
    private long lastEdit;

    String query() {
        return text.toString().trim().toLowerCase(Locale.ROOT);
    }

    boolean isEmpty() {
        return text.isEmpty();
    }

    void clear() {
        text.setLength(0);
    }

    void render(GuiContext ctx, float x, float y) {
        Render2D r = ctx.render;
        active.animateTo(text.isEmpty() ? 0 : 1);
        float a = active.get();

        r.shadow(x, y, WIDTH, HEIGHT, HEIGHT / 2f, 10f, 0x80000000);
        r.roundedRect(x, y, WIDTH, HEIGHT, HEIGHT / 2f, Theme.PANEL);
        r.roundedOutline(x, y, WIDTH, HEIGHT, HEIGHT / 2f, 0.7f, Colors.lerp(Theme.OUTLINE, ctx.theme.accent(170), a));

        int iconColor = Colors.lerp(Theme.TEXT_MUTED, ctx.theme.accent(), a);
        float iconX = x + 11f, iconY = y + HEIGHT / 2f - 0.8f;
        r.ring(iconX, iconY, 3.3f, 1.1f, iconColor);
        r.line(iconX + 2.5f, iconY + 2.5f, iconX + 4.6f, iconY + 4.6f, 1.2f, iconColor);

        float textX = x + 20f;
        float textY = Widgets.textY(y, HEIGHT);
        if (text.isEmpty()) {
            r.text("Search modules...", textX, textY, Theme.TEXT_MUTED);
        } else {
            float width = r.text(text.toString(), textX, textY, Theme.TEXT);
            // Solid right after typing, then blink.
            boolean caretOn = System.currentTimeMillis() - lastEdit < 500 || (System.currentTimeMillis() / 530) % 2 == 0;
            if (caretOn) r.rect(textX + width + 1f, y + 5f, 0.75f, HEIGHT - 10f, ctx.theme.accent());
            String hint = "esc";
            r.text(hint, x + WIDTH - 10f - r.textWidth(hint), textY, Colors.fade(Theme.TEXT_MUTED, a));
        }
    }

    boolean charTyped(int codepoint) {
        if (text.length() >= MAX_LENGTH || Character.isISOControl(codepoint)) return false;
        text.appendCodePoint(codepoint);
        lastEdit = System.currentTimeMillis();
        return true;
    }

    boolean keyPressed(int key, boolean control) {
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (text.isEmpty()) return false;
                if (control) text.setLength(0);
                else text.deleteCharAt(text.length() - 1);
            }
            case GLFW.GLFW_KEY_V -> {
                if (!control) return false;
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("\\p{Cntrl}", "");
                text.append(clip, 0, Math.min(clip.length(), MAX_LENGTH - text.length()));
            }
            default -> {
                return false;
            }
        }
        lastEdit = System.currentTimeMillis();
        return true;
    }
}
