package dev.vanguard.gui.clickgui;

import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Module search. Typing anywhere in the ClickGUI goes here. */
final class SearchField {
    static final float HEIGHT = 17f;
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

    void render(GuiContext ctx, float x, float y, float width) {
        Render2D r = ctx.render;
        active.animateTo(text.isEmpty() ? 0 : 1);
        float a = active.get();

        r.roundedRect(x, y, width, HEIGHT, 5f, Theme.FIELD);
        r.roundedOutline(x, y, width, HEIGHT, 5f, 0.6f, Colors.lerp(Theme.SEPARATOR, ctx.theme.accent(170), a));
        Icons.search(r, x + 9f, y + HEIGHT / 2f, 8f, Colors.lerp(Theme.TEXT_MUTED, ctx.theme.accent(), a));

        float textX = x + 17f;
        float textY = r.textY(y, HEIGHT);
        if (text.isEmpty()) {
            r.text("Search", textX, textY, Theme.TEXT_MUTED);
        } else {
            String shown = r.ellipsize(text.toString(), width - 40f, false);
            float textWidth = r.text(shown, textX, textY, Theme.TEXT);
            // Solid right after typing, then blink.
            boolean caretOn = System.currentTimeMillis() - lastEdit < 500 || (System.currentTimeMillis() / 530) % 2 == 0;
            if (caretOn) r.rect(textX + textWidth + 1f, y + 4.5f, 0.75f, HEIGHT - 9f, ctx.theme.accent());
            String hint = "esc";
            r.small(hint, x + width - 7f - r.smallWidth(hint), r.smallY(y, HEIGHT), Colors.fade(Theme.TEXT_MUTED, a));
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
