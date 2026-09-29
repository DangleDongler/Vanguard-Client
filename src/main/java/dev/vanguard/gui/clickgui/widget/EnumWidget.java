package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.setting.EnumSetting;
import org.lwjgl.glfw.GLFW;

/** Mode picker: the value in a box on the right. Left-click opens the options, right-click cycles. */
public final class EnumWidget extends Widget {
    private static final float BOX_H = 14f;
    private static final float OPTION_HEIGHT = 15f;
    private static final float LIST_PAD = 3f;

    private final EnumSetting<?> choice;
    private final Animation open = new Animation(0, 220, Easing.QUINT_OUT);
    private final Animation hover = new Animation(0, 120, Easing.LINEAR);
    private final Animation[] optionHover;
    private boolean expanded;

    public EnumWidget(EnumSetting<?> setting) {
        super(setting);
        this.choice = setting;
        this.optionHover = new Animation[setting.constants().length];
        for (int i = 0; i < optionHover.length; i++) optionHover[i] = new Animation(0, 120, Easing.LINEAR);
    }

    private float listHeight() {
        return choice.constants().length * OPTION_HEIGHT + LIST_PAD * 2 + 4f;
    }

    @Override
    public float height() {
        return ROW_HEIGHT + listHeight() * open.get();
    }

    @Override
    protected void draw(GuiContext ctx) {
        Render2D r = ctx.render;
        open.animateTo(expanded ? 1 : 0);
        float progress = open.get();

        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        if (hovered) {
            ctx.tooltip(description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);
        float h = hover.get();

        drawHover(ctx, ROW_HEIGHT, h);
        String value = EnumSetting.displayName(choice.get());
        float boxW = Math.max(44f, r.smallWidth(value) + 16f);
        float boxX = x + width - PAD_X - boxW, boxY = y + (ROW_HEIGHT - BOX_H) / 2f;
        drawLabel(ctx, y, ROW_HEIGHT, boxW, Theme.TEXT);
        r.roundedRect(boxX, boxY, boxW, BOX_H, 3f, Colors.lerp(Theme.FIELD, Theme.BUTTON, Math.max(h * 0.6f, progress)));
        r.small(value, boxX + 8f, r.smallY(boxY, BOX_H), Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(h, progress)));

        if (progress <= 0.001f) return;
        float listY = y + ROW_HEIGHT + 2f;
        float visible = (listHeight() - 2f) * progress;
        float listX = x + PAD_X, listW = width - PAD_X * 2;
        r.pushScissor(x, listY, width, visible);
        r.pushAlpha(progress);
        r.roundedRect(listX, listY, listW, listHeight() - 4f, 4f, Theme.FIELD);
        Enum<?>[] constants = choice.constants();
        for (int i = 0; i < constants.length; i++) {
            float oy = listY + LIST_PAD + i * OPTION_HEIGHT;
            boolean selected = constants[i] == choice.get();
            boolean optionHovered = ctx.hovered(listX, oy, listW, OPTION_HEIGHT) && ctx.mouseY < listY + visible;
            if (optionHovered) ctx.cursor(CursorTypes.POINTING_HAND);
            optionHover[i].animateTo(optionHovered ? 1 : 0);

            float oh = optionHover[i].get();
            if (oh > 0.001f) r.roundedRect(listX + 2f, oy, listW - 4f, OPTION_HEIGHT, 3f, Colors.fade(Theme.BUTTON, oh));
            int color = selected ? Theme.TEXT : Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, oh);
            r.small(EnumSetting.displayName(constants[i]), listX + 8f, r.smallY(oy, OPTION_HEIGHT), color);
            if (selected) r.circle(listX + listW - 8f, oy + OPTION_HEIGHT / 2f, 1.6f, ctx.theme.accent());
        }
        r.popAlpha();
        r.popScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inRow(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) expanded = !expanded;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) choice.cycle(1);
            else return false;
            return true;
        }
        if (!expanded || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        float listY = y + ROW_HEIGHT + 2f + LIST_PAD;
        if (!contains(mouseX, mouseY, x, y + ROW_HEIGHT, width, listHeight() * open.get())) {
            expanded = false;
            return false;
        }
        int index = (int) Math.floor((mouseY - listY) / OPTION_HEIGHT);
        if (index >= 0 && index < choice.constants().length) {
            select(index);
            expanded = false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void select(int index) {
        ((EnumSetting) choice).set(choice.constants()[index]);
    }
}
