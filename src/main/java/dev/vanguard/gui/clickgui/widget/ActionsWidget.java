package dev.vanguard.gui.clickgui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.clickgui.GuiContext;
import dev.vanguard.gui.clickgui.Theme;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Render2D;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A label with a few text buttons on the right. A risky button asks for a second click. */
public final class ActionsWidget extends Widget {
    private static final float BUTTON_H = 15f;
    private static final float GAP = 3f;
    private static final long CONFIRM_MS = 3000;

    /** One button. */
    public static final class Action {
        final String label;
        final String tooltip;
        final Runnable run;
        final boolean confirm;
        final Animation hover = new Animation(0, 120, Easing.LINEAR);
        float bx, bw;
        long armedAt;

        public Action(String label, String tooltip, boolean confirm, Runnable run) {
            this.label = label;
            this.tooltip = tooltip;
            this.confirm = confirm;
            this.run = run;
        }

        boolean armed() {
            return confirm && System.currentTimeMillis() - armedAt < CONFIRM_MS;
        }

        String shownLabel() {
            return armed() ? "Confirm" : label;
        }
    }

    private final List<Action> actions = new ArrayList<>();
    private final int labelColor;

    public ActionsWidget(String label, String description, int labelColor, Action... actions) {
        super(null, label, description);
        this.labelColor = labelColor;
        this.actions.addAll(List.of(actions));
    }

    @Override
    public float height() {
        return ROW_HEIGHT;
    }

    @Override
    protected void draw(GuiContext ctx) {
        Render2D r = ctx.render;
        boolean hovered = ctx.hovered(x, y, width, ROW_HEIGHT);
        float by = y + (ROW_HEIGHT - BUTTON_H) / 2f;

        float right = x + width - PAD_X;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action action = actions.get(i);
            action.bw = r.smallWidth(action.shownLabel()) + 18f;
            action.bx = right - action.bw;
            right = action.bx - GAP;
        }
        String tooltip = description();
        for (Action action : actions) {
            boolean over = ctx.hovered(action.bx, by, action.bw, BUTTON_H);
            if (over) {
                ctx.cursor(CursorTypes.POINTING_HAND);
                tooltip = action.armed() ? "Click again to confirm." : action.tooltip;
            }
            action.hover.animateTo(over ? 1 : 0);
        }
        if (hovered) ctx.tooltip(tooltip);

        drawHover(ctx, ROW_HEIGHT, hovered ? 1 : 0);
        drawLabel(ctx, y, ROW_HEIGHT, x + width - PAD_X - (right + GAP), labelColor);
        for (Action action : actions) {
            float h = action.hover.get();
            boolean armed = action.armed();
            int fill = armed ? Colors.withAlpha(Theme.DANGER, 70) : Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h);
            r.roundedRect(action.bx, by, action.bw, BUTTON_H, BUTTON_H / 2f, fill);
            int text = armed ? Theme.DANGER : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, h);
            r.small(action.shownLabel(), action.bx + 9f, r.smallY(by, BUTTON_H), text);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inRow(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        float by = y + (ROW_HEIGHT - BUTTON_H) / 2f;
        for (Action action : actions) {
            if (!contains(mouseX, mouseY, action.bx, by, action.bw, BUTTON_H)) continue;
            if (action.confirm && !action.armed()) {
                action.armedAt = System.currentTimeMillis();
            } else {
                action.armedAt = 0;
                action.run.run();
            }
            return true;
        }
        return true;
    }
}
