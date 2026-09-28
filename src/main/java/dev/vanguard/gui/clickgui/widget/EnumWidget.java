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

/** Mode picker: left-click opens a dropdown, right-click cycles to the next option. */
public final class EnumWidget extends Widget {
    private static final float OPTION_HEIGHT = 12f;

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
        return choice.constants().length * OPTION_HEIGHT + 3f;
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
            ctx.tooltip(setting.description());
            ctx.cursor(CursorTypes.POINTING_HAND);
        }
        hover.animateTo(hovered ? 1 : 0);

        r.rect(x, y, width, ROW_HEIGHT, Colors.fade(Theme.HOVER, hover.get()));
        float textY = Widgets.textY(y, ROW_HEIGHT);

        // Value in a dropdown box on the right.
        String value = EnumSetting.displayName(choice.get());
        float boxW = Math.max(40f, r.textWidth(value) + 18f), boxH = 11f;
        float boxX = x + width - PAD_X - boxW, boxY = y + (ROW_HEIGHT - boxH) / 2f;
        drawLabel(ctx, textY, boxW, Theme.TEXT);
        r.roundedRect(boxX, boxY, boxW, boxH, 3.5f, Theme.FIELD);
        r.roundedOutline(boxX, boxY, boxW, boxH, 3.5f, 0.6f,
            Colors.lerp(Colors.lerp(Theme.SEPARATOR, Theme.OUTLINE, hover.get()), ctx.theme.accent(150), progress));
        r.text(value, boxX + 5f, Widgets.textY(boxY, boxH), Theme.TEXT);
        r.chevron(boxX + boxW - 6.5f, boxY + boxH / 2f, 3.6f, (float) (Math.PI / 2 - Math.PI * progress), 1f,
            Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, Math.max(hover.get(), progress)));

        if (progress <= 0.001f) return;
        float listY = y + ROW_HEIGHT;
        float visible = listHeight() * progress;
        r.pushScissor(x, listY, width, visible);
        r.pushAlpha(progress);
        Enum<?>[] constants = choice.constants();
        for (int i = 0; i < constants.length; i++) {
            float oy = listY + i * OPTION_HEIGHT;
            boolean selected = constants[i] == choice.get();
            boolean optionHovered = ctx.hovered(x, oy, width, OPTION_HEIGHT) && ctx.mouseY < listY + visible;
            if (optionHovered) ctx.cursor(CursorTypes.POINTING_HAND);
            optionHover[i].animateTo(optionHovered ? 1 : 0);

            r.rect(x + PAD_X - 3f, oy, width - (PAD_X - 3f) * 2, OPTION_HEIGHT, Colors.fade(Theme.HOVER, optionHover[i].get()));
            float dotX = x + PAD_X + 2f;
            if (selected) r.circle(dotX, oy + OPTION_HEIGHT / 2f, 1.6f, ctx.theme.accent());
            int color = selected ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, optionHover[i].get());
            r.text(EnumSetting.displayName(constants[i]), dotX + 5f, Widgets.textY(oy, OPTION_HEIGHT), color);
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
        float listY = y + ROW_HEIGHT;
        if (!contains(mouseX, mouseY, x, listY, width, listHeight() * open.get())) return false;
        int index = (int) ((mouseY - listY) / OPTION_HEIGHT);
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
