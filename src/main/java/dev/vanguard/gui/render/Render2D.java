package dev.vanguard.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;

/**
 * Immediate-mode 2D drawing on top of Minecraft's deferred GUI renderer, with
 * float coordinates, anti-aliased rounded shapes, soft shadows, a global alpha
 * multiplier (for fades) and the client font.
 *
 * <p>Every shape becomes one {@link ShapeRenderState}. Minecraft layers GUI elements by
 * their bounds, so anything drawn later that overlaps an earlier element ends up above it.
 */
public final class Render2D {
    private static final int SOLID = 0;
    private static final int SOFT = 1;

    private final Minecraft mc = Minecraft.getInstance();
    private GuiGraphicsExtractor graphics;

    private float alpha = 1f;
    private float[] alphaStack = new float[8];
    private int alphaDepth;

    private boolean customFont;
    /** 0 until the first frame picks one, so the styles below always get built. */
    private int oversample;
    private Style regularStyle = Style.EMPTY;
    private Style boldStyle = Style.EMPTY.withBold(true);

    // Shape under construction.
    private float[] vertices = new float[64 * ShapeRenderState.FLOATS_PER_VERTEX];
    private int[] colors = new int[64];
    private int vertexCount;
    private float minX, minY, maxX, maxY;

    /**
     * Starts a frame.
     *
     * @param customFont  use the bundled Inter font instead of Minecraft's
     * @param pixelsPerUnit physical pixels per GUI unit, used to pick a crisp font rasterization
     */
    public void begin(GuiGraphicsExtractor graphics, boolean customFont, float pixelsPerUnit) {
        this.graphics = graphics;
        this.alpha = 1f;
        this.alphaDepth = 0;
        int wantedOversample = Math.clamp(Math.round(pixelsPerUnit), 1, 4);
        if (customFont != this.customFont || wantedOversample != oversample) {
            this.customFont = customFont;
            this.oversample = wantedOversample;
            if (customFont) {
                regularStyle = Style.EMPTY.withFont(new FontDescription.Resource(dev.vanguard.Vanguard.id("inter_" + oversample)));
                boldStyle = Style.EMPTY.withFont(new FontDescription.Resource(dev.vanguard.Vanguard.id("inter_bold_" + oversample)));
            } else {
                regularStyle = Style.EMPTY;
                boldStyle = Style.EMPTY.withBold(true);
            }
        }
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public Matrix3x2fStack pose() {
        return graphics.pose();
    }

    // ---------------------------------------------------------------- alpha

    /** Multiplies the alpha of everything drawn until the matching {@link #popAlpha()}. */
    public void pushAlpha(float factor) {
        if (alphaDepth == alphaStack.length) alphaStack = Arrays.copyOf(alphaStack, alphaDepth * 2);
        alphaStack[alphaDepth++] = alpha;
        alpha *= Math.clamp(factor, 0f, 1f);
    }

    public void popAlpha() {
        alpha = alphaStack[--alphaDepth];
    }

    private int applyAlpha(int color) {
        return Colors.fade(color, alpha);
    }

    // ---------------------------------------------------------------- scissor

    public void pushScissor(float x, float y, float w, float h) {
        graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    public void popScissor() {
        graphics.disableScissor();
    }

    // ---------------------------------------------------------------- shapes

    public void rect(float x, float y, float w, float h, int color) {
        gradient(x, y, w, h, color, color, color, color);
    }

    public void gradientH(float x, float y, float w, float h, int left, int right) {
        gradient(x, y, w, h, left, right, right, left);
    }

    public void gradientV(float x, float y, float w, float h, int top, int bottom) {
        gradient(x, y, w, h, top, top, bottom, bottom);
    }

    public void gradient(float x, float y, float w, float h, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (w <= 0 || h <= 0) return;
        beginShape();
        quad(x, y, x + w, y + h, 0, 0, 0, 0, SOLID, 0,
            applyAlpha(topLeft), applyAlpha(topRight), applyAlpha(bottomRight), applyAlpha(bottomLeft));
        submitShape();
    }

    public void roundedRect(float x, float y, float w, float h, float radius, int color) {
        roundedRect(x, y, w, h, radius, radius, radius, radius, color);
    }

    /** Rounded rectangle with individual corner radii (top-left, top-right, bottom-right, bottom-left). */
    public void roundedRect(float x, float y, float w, float h, float tl, float tr, float br, float bl, int color) {
        if (w <= 0 || h <= 0) return;
        float limit = Math.min(w, h) / 2f;
        tl = Math.clamp(tl, 0, limit);
        tr = Math.clamp(tr, 0, limit);
        br = Math.clamp(br, 0, limit);
        bl = Math.clamp(bl, 0, limit);
        int c = applyAlpha(color);
        if (Colors.alpha(c) == 0) return;

        float x2 = x + w, y2 = y + h;
        float top = Math.max(tl, tr);
        float bottom = Math.max(bl, br);

        beginShape();
        // Corners: uv runs from the corner's circle center (0) to its edge (1).
        solidQuad(x, y, x + tl, y + tl, 1, 1, 0, 0, c);
        solidQuad(x2 - tr, y, x2, y + tr, 0, 1, 1, 0, c);
        solidQuad(x2 - br, y2 - br, x2, y2, 0, 0, 1, 1, c);
        solidQuad(x, y2 - bl, x + bl, y2, 1, 0, 0, 1, c);
        // Fill beside the smaller corner of each band, then the bands themselves.
        solidQuad(x, y + tl, x + tl, y + top, 0, 0, 0, 0, c);
        solidQuad(x2 - tr, y + tr, x2, y + top, 0, 0, 0, 0, c);
        solidQuad(x, y2 - bottom, x + bl, y2 - bl, 0, 0, 0, 0, c);
        solidQuad(x2 - br, y2 - bottom, x2, y2 - br, 0, 0, 0, 0, c);
        solidQuad(x + tl, y, x2 - tr, y + top, 0, 0, 0, 0, c);
        solidQuad(x + bl, y2 - bottom, x2 - br, y2, 0, 0, 0, 0, c);
        solidQuad(x, y + top, x2, y2 - bottom, 0, 0, 0, 0, c);
        submitShape();
    }

    /** Outline of a rounded rectangle, drawn inside its bounds. */
    public void roundedOutline(float x, float y, float w, float h, float radius, float thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        float r = Math.clamp(Math.max(radius, thickness), 0, Math.min(w, h) / 2f);
        int c = applyAlpha(color);
        if (Colors.alpha(c) == 0) return;
        int hole = Math.clamp(Math.round((r - thickness) / r * 255f), 0, 255);
        float x2 = x + w, y2 = y + h;

        beginShape();
        quad(x, y, x + r, y + r, 1, 1, 0, 0, SOLID, hole, c, c, c, c);
        quad(x2 - r, y, x2, y + r, 0, 1, 1, 0, SOLID, hole, c, c, c, c);
        quad(x2 - r, y2 - r, x2, y2, 0, 0, 1, 1, SOLID, hole, c, c, c, c);
        quad(x, y2 - r, x + r, y2, 1, 0, 0, 1, SOLID, hole, c, c, c, c);
        solidQuad(x + r, y, x2 - r, y + thickness, 0, 0, 0, 0, c);
        solidQuad(x + r, y2 - thickness, x2 - r, y2, 0, 0, 0, 0, c);
        solidQuad(x, y + r, x + thickness, y2 - r, 0, 0, 0, 0, c);
        solidQuad(x2 - thickness, y + r, x2, y2 - r, 0, 0, 0, 0, c);
        submitShape();
    }

    public void circle(float centerX, float centerY, float radius, int color) {
        roundedRect(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
    }

    public void ring(float centerX, float centerY, float radius, float thickness, int color) {
        roundedOutline(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, thickness, color);
    }

    /**
     * Soft shadow (or glow) around a rounded rectangle, extending {@code size} units outward
     * and fading to transparent.
     */
    public void shadow(float x, float y, float w, float h, float radius, float size, int color) {
        if (w <= 0 || h <= 0 || size <= 0) return;
        float r = Math.clamp(radius, 0, Math.min(w, h) / 2f);
        int c = applyAlpha(color);
        if (Colors.alpha(c) == 0) return;
        float outer = r + size;
        int inner = Math.clamp(Math.round(r / outer * 255f), 0, 255);
        float x2 = x + w, y2 = y + h;
        float cx1 = x + r, cy1 = y + r, cx2 = x2 - r, cy2 = y2 - r;

        beginShape();
        quad(x - size, y - size, cx1, cy1, 1, 1, 0, 0, SOFT, inner, c, c, c, c);
        quad(cx2, y - size, x2 + size, cy1, 0, 1, 1, 0, SOFT, inner, c, c, c, c);
        quad(cx2, cy2, x2 + size, y2 + size, 0, 0, 1, 1, SOFT, inner, c, c, c, c);
        quad(x - size, cy2, cx1, y2 + size, 1, 0, 0, 1, SOFT, inner, c, c, c, c);
        quad(cx1, y - size, cx2, cy1, 0, 1, 0, 0, SOFT, inner, c, c, c, c);
        quad(cx1, cy2, cx2, y2 + size, 0, 0, 0, 1, SOFT, inner, c, c, c, c);
        quad(x - size, cy1, cx1, cy2, 1, 0, 0, 0, SOFT, inner, c, c, c, c);
        quad(cx2, cy1, x2 + size, cy2, 0, 0, 1, 0, SOFT, inner, c, c, c, c);
        quad(cx1, cy1, cx2, cy2, 0, 0, 0, 0, SOFT, inner, c, c, c, c);
        submitShape();
    }

    /** A line segment with round caps. */
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        float half = thickness / 2f;
        roundedRect(-half, -half, length + thickness, thickness, half, color);
        pose.popMatrix();
    }

    /**
     * A chevron pointing right, rotated by {@code angle} radians around its center
     * (pi/2 points it down).
     */
    public void chevron(float centerX, float centerY, float size, float angle, float thickness, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.rotate(angle);
        float h = size / 2f;
        float w = size / 4f;
        line(-w, -h, w, 0, thickness, color);
        line(-w, h, w, 0, thickness, color);
        pose.popMatrix();
    }

    /** Checkerboard used behind translucent color previews. */
    public void checkerboard(float x, float y, float w, float h, float cell, int light, int dark) {
        if (w <= 0 || h <= 0) return;
        int a = applyAlpha(light), b = applyAlpha(dark);
        beginShape();
        int row = 0;
        for (float cy = y; cy < y + h; cy += cell, row++) {
            float cy2 = Math.min(cy + cell, y + h);
            int column = 0;
            for (float cx = x; cx < x + w; cx += cell, column++) {
                int c = ((row + column) & 1) == 0 ? a : b;
                quad(cx, cy, Math.min(cx + cell, x + w), cy2, 0, 0, 0, 0, SOLID, 0, c, c, c, c);
            }
        }
        submitShape();
    }

    // ---------------------------------------------------------------- text

    public float lineHeight() {
        return 9f;
    }

    public float textWidth(String text) {
        return textWidth(text, false);
    }

    public float textWidth(String text, boolean bold) {
        return mc.font.getSplitter().stringWidth(sequence(text, bold));
    }

    /** Draws text with its top at {@code y}; returns the drawn width. */
    public float text(String text, float x, float y, int color) {
        return text(text, x, y, color, false);
    }

    public float text(String text, float x, float y, int color, boolean bold) {
        FormattedCharSequence sequence = sequence(text, bold);
        int c = applyAlpha(color);
        // Below a few alpha steps text looks like noise, and is invisible anyway.
        if (Colors.alpha(c) > 3) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            // Minecraft's pixel font needs its drop shadow to read well; Inter doesn't.
            graphics.text(mc.font, sequence, 0, 0, c, !customFont);
            pose.popMatrix();
        }
        return mc.font.getSplitter().stringWidth(sequence);
    }

    public void textCentered(String text, float centerX, float y, int color, boolean bold) {
        text(text, centerX - textWidth(text, bold) / 2f, y, color, bold);
    }

    /** Cuts {@code text} to fit {@code maxWidth}, adding an ellipsis when shortened. */
    public String ellipsize(String text, float maxWidth, boolean bold) {
        if (textWidth(text, bold) <= maxWidth) return text;
        String ellipsis = "…";
        float budget = maxWidth - textWidth(ellipsis, bold);
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end), bold) > budget) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }

    private FormattedCharSequence sequence(String text, boolean bold) {
        return FormattedCharSequence.forward(text, bold ? boldStyle : regularStyle);
    }

    public Font font() {
        return mc.font;
    }

    // ---------------------------------------------------------------- shape building

    private void beginShape() {
        vertexCount = 0;
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
    }

    private void solidQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
        quad(x0, y0, x1, y1, u0, v0, u1, v1, SOLID, 0, color, color, color, color);
    }

    /**
     * Adds an axis-aligned quad. u varies with x and v with y; see shape.fsh for how
     * mode and param are packed into the texture coordinates.
     */
    private void quad(float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      int mode, int param,
                      int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (x1 - x0 <= 0 || y1 - y0 <= 0) return;
        float modeOffset = 2f * mode + 0.5f;
        float paramOffset = 2f * param + 0.5f;
        vertex(x0, y0, u0 + modeOffset, v0 + paramOffset, topLeft);
        vertex(x0, y1, u0 + modeOffset, v1 + paramOffset, bottomLeft);
        vertex(x1, y1, u1 + modeOffset, v1 + paramOffset, bottomRight);
        vertex(x1, y0, u1 + modeOffset, v0 + paramOffset, topRight);
        minX = Math.min(minX, x0);
        minY = Math.min(minY, y0);
        maxX = Math.max(maxX, x1);
        maxY = Math.max(maxY, y1);
    }

    private void vertex(float x, float y, float u, float v, int color) {
        int o = vertexCount * ShapeRenderState.FLOATS_PER_VERTEX;
        if (o + ShapeRenderState.FLOATS_PER_VERTEX > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        vertices[o] = x;
        vertices[o + 1] = y;
        vertices[o + 2] = u;
        vertices[o + 3] = v;
        colors[vertexCount++] = color;
    }

    private void submitShape() {
        if (vertexCount == 0) return;
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top)
            .transformMaxBounds(pose);
        ScreenRectangle scissor = graphics.scissorStack.peek();
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) return;
        }
        graphics.guiRenderState.addGuiElement(new ShapeRenderState(
            pose,
            Arrays.copyOf(vertices, vertexCount * ShapeRenderState.FLOATS_PER_VERTEX),
            Arrays.copyOf(colors, vertexCount),
            vertexCount,
            scissor,
            bounds));
    }
}
