package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Swatch that expands into a saturation/value square, hue bar, optional alpha bar and copy/paste. */
public final class ColorWidget extends Widget {
    private static final float SV_HEIGHT = 52f;
    private static final float BAR_HEIGHT = 6f;
    private static final float GAP = 4f;
    private static final float BUTTON_HEIGHT = 11f;
    private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    private enum Drag { NONE, SV, HUE, ALPHA }

    private final ColorSetting color;
    private final Animation open = new Animation(0, 240, Easing.QUINT_OUT);
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);
    private boolean expanded;
    private Drag drag = Drag.NONE;

    private float hue;
    private float saturation;
    private float value;
    private int alpha;
    private int synced;

    // Layout from the last frame, used for hit testing.
    private float pickerX, pickerW, svY, hueY, alphaY, buttonsY;
    private float copyX, copyW, pasteX, pasteW;

    public ColorWidget(ColorSetting setting) {
        super(setting);
        this.color = setting;
        syncFromSetting();
    }

    private void syncFromSetting() {
        int argb = color.argb();
        float[] hsv = Colors.toHsv(argb);
        // Keep the current hue when the color is grey, so dragging to the corner doesn't lose it.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        alpha = Colors.alpha(argb);
        synced = argb;
    }

    private void apply() {
        color.set(Colors.hsv(hue, saturation, value, alpha));
        synced = color.argb();
    }

    private float pickerHeight() {
        float h = GAP + SV_HEIGHT + GAP + BAR_HEIGHT;
        if (color.allowsAlpha()) h += GAP + BAR_HEIGHT;
        return h + GAP + BUTTON_HEIGHT + GAP + 1f;
    }

    @Override
    public float height() {
        return ROW_HEIGHT + pickerHeight() * open.get();
    }

    @Override
    protected void draw(GuiContext ctx) {
        if (color.argb() != synced) syncFromSetting();
        Render2D r = ctx.render;
        open.animateTo(expanded ? 1 : 0);
        float progress = open.get();

        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        if (hovered) {
            ctx.tooltip(description() + " Right-click to reset.");
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);

        drawHover(ctx, ROW_HEIGHT, hover.get());
        float swatchW = 24f, swatchH = 13f;
        drawLabel(ctx, y, ROW_HEIGHT, swatchW, Theme.TEXT);
        float sx = x + width - PAD_X - swatchW, sy = y + (ROW_HEIGHT - swatchH) / 2f;
        if (Colors.alpha(color.argb()) < 255) {
            r.pushScissor(sx, sy, swatchW, swatchH);
            r.checkerboard(sx, sy, swatchW, swatchH, 2f, 0xFF9A9A9A, 0xFF5A5A5A);
            r.popScissor();
        }
        r.roundedRect(sx, sy, swatchW, swatchH, swatchH / 2f, color.argb());
        r.roundedOutline(sx, sy, swatchW, swatchH, swatchH / 2f, 0.7f, 0x40FFFFFF);

        pickerX = x + PAD_X;
        pickerW = width - PAD_X * 2;
        svY = y + ROW_HEIGHT + GAP;
        hueY = svY + SV_HEIGHT + GAP;
        alphaY = hueY + BAR_HEIGHT + GAP;
        buttonsY = (color.allowsAlpha() ? alphaY + BAR_HEIGHT : hueY + BAR_HEIGHT) + GAP;

        if (drag != Drag.NONE) updateDrag(ctx.rawMouseX, ctx.rawMouseY);
        if (progress <= 0.001f) return;

        r.pushScissor(x, y + ROW_HEIGHT, width, pickerHeight() * progress);
        r.pushAlpha(progress);

        // Saturation (x) / value (y): white -> hue horizontally, then transparent -> black vertically.
        int pureHue = Colors.hsv(hue, 1f, 1f, 255);
        r.gradientH(pickerX, svY, pickerW, SV_HEIGHT, 0xFFFFFFFF, pureHue);
        r.gradientV(pickerX, svY, pickerW, SV_HEIGHT, 0x00000000, 0xFF000000);
        r.roundedOutline(pickerX - 0.5f, svY - 0.5f, pickerW + 1f, SV_HEIGHT + 1f, 1.5f, 0.6f, Theme.HAIRLINE);
        float cx = pickerX + saturation * pickerW, cy = svY + (1f - value) * SV_HEIGHT;
        r.ring(cx, cy, 3f, 1.2f, 0xFFFFFFFF);
        r.ring(cx, cy, 3.6f, 0.6f, 0x80000000);

        float segment = pickerW / (HUES.length - 1);
        for (int i = 0; i < HUES.length - 1; i++) {
            r.gradientH(pickerX + i * segment, hueY, segment, BAR_HEIGHT, HUES[i], HUES[i + 1]);
        }
        drawBarKnob(r, pickerX + hue * pickerW, hueY);

        if (color.allowsAlpha()) {
            r.pushScissor(pickerX, alphaY, pickerW, BAR_HEIGHT);
            r.checkerboard(pickerX, alphaY, pickerW, BAR_HEIGHT, 3f, 0xFF9A9AA6, 0xFF5A5A66);
            r.popScissor();
            int opaque = color.argb() | 0xFF000000;
            r.gradientH(pickerX, alphaY, pickerW, BAR_HEIGHT, opaque & 0x00FFFFFF, opaque);
            drawBarKnob(r, pickerX + alpha / 255f * pickerW, alphaY);
        }

        r.small(Colors.hex(color.argb(), color.allowsAlpha()), pickerX, r.smallY(buttonsY, BUTTON_HEIGHT), Theme.TEXT_DIM);
        pasteW = BUTTON_HEIGHT;
        copyW = BUTTON_HEIGHT;
        pasteX = pickerX + pickerW - pasteW;
        copyX = pasteX - 3f - copyW;
        if (drawButton(ctx, copyX, buttonsY, copyW)) ctx.tooltip("Copy hex color");
        drawCopyIcon(r, copyX + copyW / 2f, buttonsY + BUTTON_HEIGHT / 2f, iconColor(ctx, copyX));
        if (drawButton(ctx, pasteX, buttonsY, pasteW)) ctx.tooltip("Paste hex color (#RRGGBB or #AARRGGBB)");
        drawPasteIcon(r, pasteX + pasteW / 2f, buttonsY + BUTTON_HEIGHT / 2f, iconColor(ctx, pasteX));

        r.popAlpha();
        r.popScissor();
    }

    private void drawBarKnob(Render2D r, float kx, float barY) {
        r.shadow(kx - 1.5f, barY - 1f, 3f, BAR_HEIGHT + 2f, 1.5f, 2.5f, 0x80000000);
        r.roundedRect(kx - 1.5f, barY - 1f, 3f, BAR_HEIGHT + 2f, 1.5f, 0xFFFFFFFF);
    }

    /** Draws a button background; returns whether it is hovered. */
    private boolean drawButton(GuiContext ctx, float bx, float by, float bw) {
        Render2D r = ctx.render;
        boolean hovered = ctx.hovered(bx, by, bw, BUTTON_HEIGHT);
        if (hovered) ctx.cursor(CursorTypes.POINTING_HAND);
        r.roundedRect(bx, by, bw, BUTTON_HEIGHT, 3f, hovered ? Theme.CONTROL_HOVER : Theme.CONTROL);
        return hovered;
    }

    private int iconColor(GuiContext ctx, float bx) {
        return ctx.hovered(bx, buttonsY, BUTTON_HEIGHT, BUTTON_HEIGHT) ? Theme.TEXT : Theme.TEXT_DIM;
    }

    /** Two overlapping sheets. */
    private static void drawCopyIcon(Render2D r, float cx, float cy, int color) {
        r.roundedOutline(cx - 2.6f, cy - 2.6f, 3.8f, 3.8f, 0.9f, 0.7f, color);
        r.roundedRect(cx - 1.2f, cy - 1.2f, 3.8f, 3.8f, 0.9f, 0xFF3A3F48);
        r.roundedOutline(cx - 1.2f, cy - 1.2f, 3.8f, 3.8f, 0.9f, 0.7f, color);
    }

    /** A clipboard. */
    private static void drawPasteIcon(Render2D r, float cx, float cy, int color) {
        r.roundedOutline(cx - 2.3f, cy - 2.4f, 4.6f, 5.4f, 0.9f, 0.7f, color);
        r.roundedRect(cx - 1.2f, cy - 3.1f, 2.4f, 1.4f, 0.5f, color);
    }

    private void updateDrag(double mouseX, double mouseY) {
        float fx = (float) Math.clamp((mouseX - pickerX) / pickerW, 0, 1);
        switch (drag) {
            case SV -> {
                saturation = fx;
                value = 1f - (float) Math.clamp((mouseY - svY) / SV_HEIGHT, 0, 1);
            }
            case HUE -> hue = Math.min(fx, 0.9999f);
            case ALPHA -> alpha = Math.round(fx * 255f);
            case NONE -> { }
        }
        apply();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inRow(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) expanded = !expanded;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) color.reset();
            else return false;
            return true;
        }
        if (!expanded || open.get() < 0.9f || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!contains(mouseX, mouseY, x, y + ROW_HEIGHT, width, pickerHeight())) return false;

        if (contains(mouseX, mouseY, pickerX, svY, pickerW, SV_HEIGHT)) drag = Drag.SV;
        else if (contains(mouseX, mouseY, pickerX, hueY - 1, pickerW, BAR_HEIGHT + 2)) drag = Drag.HUE;
        else if (color.allowsAlpha() && contains(mouseX, mouseY, pickerX, alphaY - 1, pickerW, BAR_HEIGHT + 2)) drag = Drag.ALPHA;
        else if (contains(mouseX, mouseY, copyX, buttonsY, copyW, BUTTON_HEIGHT)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(Colors.hex(color.argb(), color.allowsAlpha()));
        } else if (contains(mouseX, mouseY, pasteX, buttonsY, pasteW, BUTTON_HEIGHT)) {
            parse(Minecraft.getInstance().keyboardHandler.getClipboard());
        }

        if (drag != Drag.NONE) updateDrag(mouseX, mouseY);
        return true;
    }

    private void parse(String text) {
        String hex = text.trim().replace("#", "");
        try {
            if (hex.length() == 6) color.set(Colors.alpha(color.argb()) << 24 | Integer.parseInt(hex, 16));
            else if (hex.length() == 8) color.set((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException ignored) {
            // Not a color; leave the setting unchanged.
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) drag = Drag.NONE;
    }
}
