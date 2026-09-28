package dev.vanguard.gui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.vanguard.gui.anim.Animation;
import dev.vanguard.gui.anim.Easing;
import dev.vanguard.gui.render.Colors;
import dev.vanguard.gui.render.Icons;
import dev.vanguard.gui.render.Render2D;
import dev.vanguard.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The docked tab manager: branding, search, one tab per category and the player's status. */
final class Sidebar {
    static final float WIDTH = 124f;
    private static final float PAD = 9f;
    private static final float RADIUS = 10f;
    private static final float TAB_HEIGHT = 18f;
    private static final float TAB_GAP = 2f;
    private static final float STATUS_HEIGHT = 32f;

    /** What the sidebar needs from the screen. */
    interface Host {
        boolean isOpen(Category category);

        void tabClicked(Category category);

        /** Number shown on a tab: enabled modules, or matches while searching. 0 hides it. */
        int badge(Category category);

        boolean searching();
    }

    private final SearchField search = new SearchField();
    private final List<Tab> tabs = new ArrayList<>();
    private final String version;

    Sidebar(List<Category> categories, String version) {
        this.version = version;
        for (Category category : categories) {
            tabs.add(new Tab(category, category == Category.CLIENT ? "Settings" : category.displayName()));
        }
    }

    SearchField search() {
        return search;
    }

    void render(GuiContext ctx, float x, float y, float height, Host host) {
        Render2D r = ctx.render;
        Theme theme = ctx.theme;

        r.shadow(x, y, WIDTH, height, RADIUS, 16f, Theme.SHADOW);
        r.roundedRect(x, y, WIDTH, height, RADIUS, Theme.SIDEBAR);
        r.roundedOutline(x, y, WIDTH, height, RADIUS, 0.6f, Theme.OUTLINE);

        // Brand: accent tile with a "V", name and version.
        float logo = 18f, logoX = x + PAD + 1f, logoY = y + PAD + 1f;
        r.shadow(logoX, logoY, logo, logo, 5f, 8f, theme.accent(80));
        r.roundedGradientV(logoX, logoY, logo, logo, 5f, theme.accentSecondary(), theme.accent());
        r.chevron(logoX + logo / 2f, logoY + logo / 2f + 0.5f, 8f, (float) (Math.PI / 2), 1.8f, 0xFFFFFFFF);
        r.text("Vanguard", logoX + logo + 7f, logoY + 1f, Theme.TEXT, true);
        r.small(version + "  ·  26.2", logoX + logo + 7f, logoY + 11f, Theme.TEXT_MUTED);

        float cy = y + PAD + 29f;
        search.render(ctx, x + PAD, cy, WIDTH - PAD * 2);
        cy += SearchField.HEIGHT + 11f;

        r.small("MODULES", x + PAD + 3f, cy, Theme.TEXT_MUTED);
        cy += 10f;

        for (Tab tab : tabs) {
            if (tab.category == Category.CLIENT) {
                cy += 3f;
                r.rect(x + PAD + 3f, cy, WIDTH - PAD * 2 - 6f, 0.6f, Theme.SEPARATOR);
                cy += 6f;
            }
            tab.render(ctx, x + PAD, cy, WIDTH - PAD * 2, x, host);
            cy += TAB_HEIGHT + TAB_GAP;
        }

        float statusY = y + height - PAD - STATUS_HEIGHT;
        if (statusY > cy + 6f) drawStatus(ctx, x + PAD - 1f, statusY, WIDTH - PAD * 2 + 2f);
    }

    /** Player face, name and where they're playing. */
    private void drawStatus(GuiContext ctx, float x, float y, float width) {
        Render2D r = ctx.render;
        Minecraft mc = Minecraft.getInstance();
        r.roundedRect(x, y, width, STATUS_HEIGHT, 7f, Theme.FIELD);
        r.roundedOutline(x, y, width, STATUS_HEIGHT, 7f, 0.6f, Theme.SEPARATOR);

        float face = 16f, faceX = x + 8f, faceY = y + (STATUS_HEIGHT - face) / 2f;
        if (mc.player != null) {
            Matrix3x2fStack pose = r.pose();
            pose.pushMatrix();
            pose.translate(faceX, faceY);
            pose.scale(face / 8f);
            PlayerFaceExtractor.extractRenderState(r.graphics(), mc.player.getSkin(), 0, 0, 8, r.withCurrentAlpha(0xFFFFFFFF));
            pose.popMatrix();
            // Round the face's corners by covering them with the card color.
            r.roundedOutline(faceX - 1f, faceY - 1f, face + 2f, face + 2f, 4f, 1.6f, Theme.FIELD);
        } else {
            r.roundedRect(faceX, faceY, face, face, 3f, Theme.TRACK);
        }

        float textX = faceX + face + 7f;
        String name = mc.getUser().getName();
        r.text(r.ellipsize(name, x + width - 6f - textX, true), textX, y + 6f, Theme.TEXT, true);

        String where;
        int statusColor = Theme.ONLINE;
        if (mc.player == null) {
            where = "Offline";
            statusColor = Theme.TEXT_MUTED;
        } else if (mc.isLocalServer()) {
            where = "Singleplayer";
        } else {
            ServerData server = mc.getCurrentServer();
            where = server != null ? server.ip : "Multiplayer";
            PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null;
            if (info != null) where += "  ·  " + info.getLatency() + "ms";
        }
        float dotY = y + 21.5f;
        r.shadow(textX, dotY - 1.8f, 3.6f, 3.6f, 1.8f, 3f, Colors.withAlpha(statusColor, 90));
        r.circle(textX + 1.8f, dotY, 1.8f, statusColor);
        r.small(r.ellipsize(where, x + width - 6f - textX - 7f, false), textX + 7f, r.smallY(dotY - 5f, 10f), Theme.TEXT_DIM);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (Tab tab : tabs) {
            if (tab.contains(mouseX, mouseY)) {
                host.tabClicked(tab.category);
                return true;
            }
        }
        return false;
    }

    private static final class Tab {
        final Category category;
        final String label;
        final Animation open = new Animation(0, 240, Easing.CUBIC_OUT);
        final Animation hover = new Animation(0, 130, Easing.LINEAR);
        float x, y, width;

        Tab(Category category, String label) {
            this.category = category;
            this.label = label;
        }

        boolean contains(double mouseX, double mouseY) {
            return width > 0 && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
        }

        void render(GuiContext ctx, float x, float y, float width, float sidebarX, Host host) {
            this.x = x;
            this.y = y;
            this.width = width;
            Render2D r = ctx.render;
            Theme theme = ctx.theme;

            boolean hovered = ctx.hovered(x, y, width, TAB_HEIGHT);
            if (hovered) {
                ctx.cursor(CursorTypes.POINTING_HAND);
                ctx.tooltip(host.searching() ? "Clear search and open " + label
                    : host.isOpen(category) ? "Close " + label : "Open " + label);
            }
            hover.animateTo(hovered ? 1 : 0);
            open.animateTo(host.isOpen(category) ? 1 : 0);
            float o = open.get(), h = hover.get();

            r.roundedRect(x, y, width, TAB_HEIGHT, 5f, Colors.fade(Theme.TAB_OPEN, o));
            r.roundedRect(x, y, width, TAB_HEIGHT, 5f, Colors.fade(Theme.HOVER, h));
            if (o > 0.01f) {
                float barHeight = 9f * o;
                float barY = y + (TAB_HEIGHT - barHeight) / 2f;
                r.shadow(sidebarX + 2.5f, barY, 2f, barHeight, 1f, 4f, theme.accent(Math.round(120 * o)));
                r.roundedRect(sidebarX + 2.5f, barY, 2f, barHeight, 1f, theme.accent());
            }

            float chip = 14f, chipX = x + 2f, chipY = y + (TAB_HEIGHT - chip) / 2f;
            r.roundedRect(chipX, chipY, chip, chip, 4f, theme.accent(Math.round(38 * o)));
            int iconColor = Colors.lerp(Colors.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, h), theme.accent(), o);
            Icons.category(r, category, chipX + chip / 2f, chipY + chip / 2f, 8.5f, iconColor);

            int labelColor = Colors.lerp(Colors.lerp(Theme.TEXT_DIM, 0xFFC9C8D3, h), Theme.TEXT, o);
            r.text(label, chipX + chip + 6f, r.textY(y, TAB_HEIGHT), labelColor);

            int badge = host.badge(category);
            if (badge > 0) {
                String text = String.valueOf(badge);
                int color = host.searching() ? theme.accent() : Theme.TEXT_MUTED;
                r.small(text, x + width - 6f - r.smallWidth(text), r.smallY(y, TAB_HEIGHT), color);
            }
        }
    }
}
