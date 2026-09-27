package dev.vanguard.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB color. */
public final class ColorSetting extends Setting<Integer> {
    private final boolean allowAlpha;

    public ColorSetting(String name, String description, int defaultArgb, boolean allowAlpha) {
        super(name, description, allowAlpha ? defaultArgb : defaultArgb | 0xFF000000);
        this.allowAlpha = allowAlpha;
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    public int argb() {
        return value;
    }

    @Override
    protected Integer sanitize(Integer newValue) {
        if (newValue == null) return value;
        return allowAlpha ? newValue : newValue | 0xFF000000;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(String.format("#%08X", value));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonPrimitive()) return;
        String hex = json.getAsString().replace("#", "");
        try {
            set((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException ignored) {
            // keep current value
        }
    }
}
