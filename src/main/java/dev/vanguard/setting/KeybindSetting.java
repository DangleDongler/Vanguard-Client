package dev.vanguard.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.vanguard.util.Keys;

/** A GLFW key code, or {@link Keys#NONE} when unbound. */
public final class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, defaultKey);
    }

    public int key() {
        return value;
    }

    public boolean isBound() {
        return value != Keys.NONE;
    }

    public boolean matches(int key) {
        return isBound() && value == key;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
    }
}
