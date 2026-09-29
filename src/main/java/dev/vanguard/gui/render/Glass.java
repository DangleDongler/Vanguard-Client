package dev.vanguard.gui.render;

/**
 * How a piece of glass looks. Sizes are in GUI units. Instances are mutable so a frame can tweak
 * one style (hover, open animation) without allocating; {@link #copy()} before changing a shared one.
 */
public final class Glass {
    /** Width of the curved edge that bends the light. */
    public float bezel = 12f;
    /** How strongly the edge bends the light; 0 is flat glass. */
    public float thickness = 1.6f;
    /** How blurred the glass is, 0 (clear) to 1 (fully frosted). */
    public float frost = 0.85f;
    /** Brightness of the rim highlight, 0 to 1. */
    public float specular = 1f;
    /** Color the glass is tinted towards, and how strongly in its alpha. */
    public int tint = 0x80101318;
    /** Size of the soft shadow below the glass; 0 for none. */
    public float shadow = 16f;
    /** Light glowing inside the glass under the cursor, 0 to 1. */
    public float glow;
    /** Cursor position in GUI units, for the glow and the highlight's direction. */
    public float cursorX = Float.NaN;
    public float cursorY = Float.NaN;

    public Glass bezel(float bezel) {
        this.bezel = bezel;
        return this;
    }

    public Glass thickness(float thickness) {
        this.thickness = thickness;
        return this;
    }

    public Glass frost(float frost) {
        this.frost = frost;
        return this;
    }

    public Glass specular(float specular) {
        this.specular = specular;
        return this;
    }

    public Glass tint(int tint) {
        this.tint = tint;
        return this;
    }

    public Glass shadow(float shadow) {
        this.shadow = shadow;
        return this;
    }

    public Glass glow(float glow, float cursorX, float cursorY) {
        this.glow = glow;
        this.cursorX = cursorX;
        this.cursorY = cursorY;
        return this;
    }

    public Glass copy() {
        Glass copy = new Glass();
        copy.bezel = bezel;
        copy.thickness = thickness;
        copy.frost = frost;
        copy.specular = specular;
        copy.tint = tint;
        copy.shadow = shadow;
        copy.glow = glow;
        copy.cursorX = cursorX;
        copy.cursorY = cursorY;
        return copy;
    }
}
