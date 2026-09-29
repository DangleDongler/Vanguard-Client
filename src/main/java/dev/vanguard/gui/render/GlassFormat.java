package dev.vanguard.gui.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * Vertex layout of a glass quad. Besides position, tint and the pixel's offset from the shape's
 * center, every vertex carries the whole shape and its optics, so one quad describes one piece of
 * glass and any number of them batch into a single draw.
 */
public final class GlassFormat {
    /** Half width, half height, corner radius and bezel width, in physical pixels. */
    public static final VertexFormatElement SHAPE = register();
    /** Thickness (refraction strength), frost, specular and opacity, 0 to 1 except thickness. */
    public static final VertexFormatElement OPTICS = register();
    /** Cursor x and y relative to the center in pixels, glow under the cursor, and shadow size in pixels. */
    public static final VertexFormatElement LIGHT = register();

    public static final VertexFormat FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("Shape", SHAPE)
        .add("Optics", OPTICS)
        .add("Light", LIGHT)
        .build();

    private GlassFormat() {
    }

    /** Takes the highest free element id, leaving the low ones to vanilla and other mods. */
    private static VertexFormatElement register() {
        for (int id = VertexFormatElement.MAX_COUNT - 1; id >= 0; id--) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
            }
        }
        throw new IllegalStateException("No free vertex element ids left for the glass renderer");
    }
}
